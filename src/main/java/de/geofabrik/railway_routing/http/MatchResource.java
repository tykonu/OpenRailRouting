package de.geofabrik.railway_routing.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.MultiException;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.http.WebHelper;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathCalculator;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.Router;
import com.graphhopper.routing.ViaRouting;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.Constants;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.gpx.GpxFromInstructions;
import com.graphhopper.util.shapes.GHPoint;
import com.opencsv.bean.CsvToBeanBuilder;

import de.geofabrik.railway_routing.InputCSVEntry;
import de.geofabrik.railway_routing.RailwayHopper;

import static com.graphhopper.util.Parameters.Routing.*;

@javax.ws.rs.Path("match")
public class MatchResource {
    private static final Logger logger = LoggerFactory.getLogger(MatchResource.class);

    private final RailwayHopper hopper;
    private final ProfileResolver profileResolver;
    private final TranslationMap trMap;

    @Inject
    public MatchResource(RailwayHopper graphHopper, ProfileResolver profileResolver,
            TranslationMap trMap) {
        this.hopper = graphHopper;
        this.profileResolver = profileResolver;
        this.trMap = trMap;
    }

    private List<Observation> readCSV(InputStream inputStream, double defaultSpeed, char separator, char quoteChar) {
        try {
            List<InputCSVEntry> inputEntries = new CsvToBeanBuilder<InputCSVEntry>(new InputStreamReader(inputStream))
                    .withType(InputCSVEntry.class)
                    .withSeparator(separator)
                    .withQuoteChar(quoteChar)
                    .build()
                    .parse();
            InputCSVEntry last = null;
            ArrayList<Observation> result = new ArrayList<Observation>(inputEntries.size());
            for (InputCSVEntry entry: inputEntries) {
                if (last != null) {
                    last = entry;
                }
                result.add(entry.toGPXEntry());
            }
            return result;
        } catch (NumberFormatException e) {
            throw new java.lang.RuntimeException(e.toString());
        }
    }

    private List<Observation> importGpx(InputStream inputStream) {
        XmlMapper xmlMapper = new XmlMapper();
        Gpx gpx;
        try {
            gpx = xmlMapper.readValue(inputStream, Gpx.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse GPX data stream.");
        }
        if (gpx.trk == null) {
            throw new IllegalArgumentException("No tracks found in GPX document. Are you using waypoints or routes instead?");
        }
        if (gpx.trk.size() > 1) {
            throw new IllegalArgumentException("GPX documents with multiple tracks not supported yet.");
        }
        return gpx.trk.get(0).getEntries();
    }

    public List<Observation> parseInput(InputStream inputStream, String contentType, char separator, char quoteChar)
            throws SAXException, IOException, ParserConfigurationException {
        if (contentType.equals(MediaType.APPLICATION_XML) || contentType.equals("application/gpx+xml")) {
            return importGpx(inputStream);
        }
        if (contentType.equals("text/csv")) {
            return readCSV(inputStream, 50, separator, quoteChar);
        }
        if (!contentType.equals(null)) {
            throw new java.lang.RuntimeException("Unsupported input MIME type");
        }
        // guess input type
        InputStreamReader isr = new InputStreamReader(inputStream);
        char[] beginning = new char[5];
        try {
            isr.read(beginning, 0, 5);
        } catch (IOException e) {
            throw new java.lang.RuntimeException(e.toString());
        }
        String declaration = String.valueOf(beginning);
        if (declaration.equals("<?xml")) {
            return importGpx(inputStream);
        }
        return readCSV(inputStream, 50, separator, quoteChar);
    }

    private String getCSVOutput(ResponsePath path, char separator) {
        PointList points = path.getPoints();
        StringBuilder str = new StringBuilder(points.getSize() * 2 * 15);
        str.append("longitude").append(separator).append("latitude\n");
        for (int i = 0; i < points.getSize(); ++i) {
            str.append(Double.toString(points.getLon(i)))
                .append(separator)
                .append(Double.toString(points.getLat(i)))
                .append('\n');
        }
        return str.toString();
    }

    /**
     * Copied from com.graphhopper.resources.MatchResource
     */
    private PMap createHintsMap(MultivaluedMap<String, String> queryParameters) {
        PMap m = new PMap();
        for (Map.Entry<String, List<String>> e : queryParameters.entrySet()) {
            if (e.getValue().size() == 1) {
                m.putObject(Helper.camelCaseToUnderScore(e.getKey()), Helper.toObject(e.getValue().get(0)));
            } else {
                // TODO ugly: ignore multi parameters like point to avoid exception. See RouteResource.initHints
            }
        }
        return m;
    }

    @POST
    // @Consumes({MediaType.APPLICATION_XML, "application/gpx+xml", "text/csv"})
    // We don't declare @Consumes types here because otherwise request without a Content-type header would fail.
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(
            InputStream inputStream,
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("type") @DefaultValue("json") String outType,
            @QueryParam("csv_input.separator") @DefaultValue(";") char csvInputSeparator,
            @QueryParam("csv_input.quoteChar") @DefaultValue("\"") char quoteChar,
            @QueryParam("csv_output.separator") @DefaultValue(";") char csvOutputSeparator,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("profile") String profile,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.Details.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("traversal_keys") @DefaultValue("false") boolean enableTraversalKeys,
            @QueryParam(MAX_VISITED_NODES) @DefaultValue("3000") int maxVisitedNodes,
            @QueryParam("gps_accuracy") @DefaultValue("40") double gpsAccuracy,
            @QueryParam("fill_gaps") @DefaultValue("false") boolean fillGaps) throws Exception {
        StopWatch sw = new StopWatch().start();
        boolean writeGPX = "gpx".equalsIgnoreCase(outType);
        instructions = writeGPX || instructions;
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String logStr = httpReq.getQueryString() + " " + infoStr;
        PMap hints = createHintsMap(uriInfo.getQueryParameters());

        if (Helper.isEmpty(profile)) {
            // resolve profile and remove legacy vehicle/weighting parameters
            // we need to explicitly disable CH here because map matching does not use it
            PMap pMap = new PMap(hints).putObject(Parameters.CH.DISABLE, true);
            profile = profileResolver.resolveProfile(pMap).getName();
            hints.remove("vehicle");
            hints.remove("weighting");
        } else if (hints.has("vehicle") || hints.has("weighting")) {
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'vehicle' and 'weighting' parameter.");
        }
        hints.putObject("profile", profile);
        hints.putObject(MAX_VISITED_NODES, maxVisitedNodes);
        Router router = hopper.createRouter();

        float took = 0;
        try {
            List<Observation> inputGPXEntries = parseInput(inputStream, httpReq.getHeader("Content-type"), csvInputSeparator, quoteChar);
            if (inputGPXEntries.size() < 2) {
                throw new IllegalArgumentException("input contains less than two points");
            }
            GraphHopperStorage storage = hopper.getGraphHopperStorage();
            Weighting weighting = hopper.createWeighting(hopper.getProfiles().get(0), hints);
                DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(minPathPrecision);
            PathMerger pathMerger = new PathMerger(storage, weighting).
                setEnableInstructions(instructions).
                setPathDetailsBuilders(hopper.getPathDetailsBuilderFactory(), pathDetails).
                setDouglasPeucker(peucker).
                setSimplifyResponse(minPathPrecision > 0);
            // set a maximum distance for routing requests
            Translation tr = trMap.getWithFallBack(Helper.getLocale(localeStr));
            List<MatchResult> matchResultsList = new ArrayList<MatchResult>(2);
            List<Path> paths = new ArrayList<Path>(3);
            Profile routingProfile = hopper.getProfile(profile);
            int start_point = 0;
            List<Observation> inputPoints;
            do {
                inputPoints = inputGPXEntries.subList(start_point, inputGPXEntries.size());
                MapMatching mapMatching = new MapMatching(hopper, hints);
                mapMatching.setMeasurementErrorSigma(gpsAccuracy);
                MatchResult matchResult = mapMatching.match(inputPoints, !fillGaps);
                if (mapMatching.getSucessfullyMatchedPoints() > 1) {
                    paths.add(matchResult.getMergedPath());
                    matchResultsList.add(matchResult);
                }
                start_point += Math.max(0, mapMatching.getSucessfullyMatchedPoints() - 1);

                // Fill gap with normal routing if matching in the last iteration of this loop ended at a gap.
                if (start_point < inputGPXEntries.size() - 1) {
                    inputPoints = inputGPXEntries.subList(start_point, inputGPXEntries.size());
                    List<GHPoint> points = new ArrayList<GHPoint>();
                    if (start_point > 0) {
                        // If we matched a part of the track, we use the last point of the match result as start.
                        PointList pointsLast = paths.get(paths.size() - 1).calcPoints();
                        points.add(pointsLast.get(pointsLast.size() - 1));
                    } else {
                        points.add((GHPoint) inputPoints.get(0).getPoint());
                    }
                    points.add((GHPoint) inputPoints.get(1).getPoint());
                    GHRequest request =  new GHRequest(points);
                    List<Snap> qResults = ViaRouting.lookup(hopper.getEncodingManager(),
                            request.getPoints(), weighting, hopper.getLocationIndex(),
                            request.getSnapPreventions(), request.getPointHints());
                    // (base) query graph used to resolve headings, curbsides etc. this is not necessarily the same thing as
                    // the (possibly implementation specific) query graph used by PathCalculator
                    QueryGraph queryGraph = QueryGraph.create(hopper.getGraphHopperStorage(), qResults);
                    TraversalMode traversalMode = routingProfile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
                    int maxVisitedNodesForRequest = request.getHints().getInt(Parameters.Routing.MAX_VISITED_NODES, hopper.getRouterConfig().getMaxVisitedNodes());
                    if (maxVisitedNodesForRequest > hopper.getRouterConfig().getMaxVisitedNodes())
                        throw new IllegalArgumentException("The max_visited_nodes parameter has to be below or equal to:" + hopper.getRouterConfig().getMaxVisitedNodes());
                    AlgorithmOptions algoOpts = AlgorithmOptions.start().
                            algorithm(request.getAlgorithm()).
                            traversalMode(traversalMode).
                            weighting(weighting).
                            maxVisitedNodes(maxVisitedNodesForRequest).
                            hints(request.getHints()).
                            build();
                    PathCalculator pathCalculator = router.createPathCalculator(queryGraph, routingProfile, algoOpts, false, false);
                    ViaRouting.Result result = ViaRouting.calcPaths(request.getPoints(), queryGraph, qResults, weighting.getFlagEncoder().getAccessEnc(), pathCalculator, request.getCurbsides(), false, request.getHeadings(), false);
                    paths.addAll(result.paths);
                    ++start_point;
                }
            } while (start_point < inputGPXEntries.size() - 1);

            ResponsePath responsePath = pathMerger.doWork(PointList.EMPTY, paths, hopper.getEncodingManager(), tr);
            responsePath.getErrors().clear();
            GHResponse rsp = new GHResponse();
            rsp.add(responsePath);

            took = sw.stop().getSeconds();
            logger.info(logStr + ", took:" + took);
            if (rsp.hasErrors()) {
                logger.error("Error merging paths: " + rsp.getErrors().toString());
                throw new MultiException(rsp.getErrors());
            }
            if (writeGPX) {
                // The GPX output is not exactly the same as upstream GraphHopper.
                // Upstream GraphHopper writes the timestamp of the first trackpoint of the input
                // file to the metadata section of the output file. We don't do this because this
                // is special to GPX. The same applies tothe name field of the metadata section.
                //TODO If elevation support is added, remove hardcoded false here.
                long time = System.currentTimeMillis();
                return Response.ok(GpxFromInstructions.createGPX(rsp.getBest().getInstructions(), "", time, false, withRoute, withTrack, false, Constants.VERSION, tr), "application/gpx+xml").
                        header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx").
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            } else if ("csv".equalsIgnoreCase(outType)) {
                return Response.ok(getCSVOutput(rsp.getBest(), csvOutputSeparator), "text/csv")
                        .header("Content-Disposition", "attachment;filename=" + "GraphHopper.csv")
                        .header("X-GH-Took", "" + Math.round(took * 1000))
                        .build();
            } else {
                ObjectNode map = WebHelper.jsonObject(rsp, instructions, calcPoints, false, pointsEncoded, took);

                double matchLength = 0, gpxEntriesLength = 0;
                int matchMillis = 0;
                List<Integer> traversalKeylist = new ArrayList<>();
                for (MatchResult mr : matchResultsList) {
                    matchLength += mr.getMatchLength();
                    matchMillis += mr.getMatchMillis();
                    gpxEntriesLength += mr.getGpxEntriesLength();
                    if (enableTraversalKeys) {
                        for (EdgeMatch em : mr.getEdgeMatches()) {
                            EdgeIteratorState edge = em.getEdgeState();
                            // encode edges as traversal keys which includes orientation, decode simply by multiplying with 0.5
                            traversalKeylist.add(GHUtility.createEdgeKey(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), false));
                        }
                    }
                }
                Map<String, Object> matchStatistics = new HashMap<>();
                matchStatistics.put("distance", matchLength);
                matchStatistics.put("time", matchMillis);
                matchStatistics.put("original_distance", gpxEntriesLength);
                map.putPOJO("map_matching", matchStatistics);

                if (enableTraversalKeys) {
                    map.putPOJO("traversal_keys", traversalKeylist);
                }
                return Response.ok(map).
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (java.lang.RuntimeException | SAXException | IOException | ParserConfigurationException err) {
            logger.error(logStr + ", took:" + took + ", error:" + err);
            throw err;
        }
    }
}
