package de.geofabrik.railway_routing.http;

import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.gpx.GpxFromInstructions;

import de.geofabrik.railway_routing.RailwayHopper;
import de.geofabrik.railway_routing.util.PatternMatching;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class RailwayMatchCommand extends ConfiguredCommand<RailwayRoutingServerConfiguration> {

    public RailwayMatchCommand() {
        super("match", "matches GPX tracks to the railway network");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("-V", "--vehicle")
                .dest("vehicle")
                .type(String.class)
                .required(true)
                .help("Profile to use");
        subparser.addArgument("-a", "--gps-accuracy")
                .dest("gps-accuracy")
                .type(Double.class)
                .required(false)
                .setDefault(40.0)
                .help("GPS measurement accuracy");
        subparser.addArgument("--transition_probability_beta")
                .type(Double.class)
                .required(false)
                .setDefault(2.0);
        subparser.addArgument("--max-nodes")
                .dest("max_nodes_to_visit")
                .type(Integer.class)
                .required(false)
                .setDefault(10000)
                .help("maximum number of nodes to visit between two trackpoints");
        subparser.addArgument("--gpx-location")
                .dest("gpx_location")
                .type(String.class)
                .required(true)
                .help("GPX input file(s). The argument may be a glob pattern.");
        subparser.addArgument("--graph_location")
                .type(String.class)
                .required(false)
                .setDefault("graph-cache")
                .help("Directory where to store the graph on disk.");
    }

    @Override
    protected void run(Bootstrap<RailwayRoutingServerConfiguration> bootstrap, Namespace args,
            RailwayRoutingServerConfiguration configuration) throws Exception {
        if (args.getString("graph_location") != null) {
            configuration.getGraphHopperConfiguration().putObject("graph.location", args.getString("graph_location"));
        }
        RailwayHopper hopper = new RailwayHopper();
        hopper.setCustomEncoderConfigs(configuration.getFlagEncoderConfigurations());
        hopper.init(configuration.getGraphHopperConfiguration());

        System.out.println(String.format("Loading graph from cache at %s", hopper.getGraphHopperLocation()));
        hopper.load(hopper.getGraphHopperLocation());
        PMap hints = new PMap().putObject(MAX_VISITED_NODES, args.get("max_visited_nodes"));
        Profile profile = hopper.getProfile(args.get("vehicle"));
        if (profile == null) {
            throw new IllegalArgumentException("The selected profile is not available in the graph.");
        }
        hints.putObject("profile", profile.getName());
        double gpsAccuracy = args.getDouble("gps-accuracy");

        Weighting weighting = hopper.createWeighting(hopper.getProfiles().get(0), hints);

        MapMatching mapMatching = new MapMatching(hopper, hints);
        mapMatching.setTransitionProbabilityBeta(args.getDouble("transition_probability_beta"));
        mapMatching.setMeasurementErrorSigma(gpsAccuracy);

        String inputPath = args.getString("gpx_location");
        if (inputPath.equals("")) {
            throw new IllegalArgumentException("No input file was given. Please use the option gpx.location=*.");
        }
        int lastSeparator = PatternMatching.patternSplitDirFile(inputPath);
        String localeStr = args.getString("instructions");
        if (localeStr == null) {
            localeStr = "";
        }
        final boolean withRoute = !localeStr.isEmpty();
        Translation tr = new TranslationMap().doImport().getWithFallBack(Helper.getLocale(localeStr));
        LinkedList<Path> files = PatternMatching.getFileList(inputPath, lastSeparator);
        XmlMapper xmlMapper = new XmlMapper();

        for (Path f : files) {
            try {
                System.out.println(String.format("Matching GPX track %s on the graph.", f.toString()));
                Gpx gpx = xmlMapper.readValue(f.toFile(), Gpx.class);
                if (gpx.trk == null) {
                    throw new IllegalArgumentException("No tracks found in GPX document. Are you using waypoints or routes instead?");
                }
                if (gpx.trk.size() > 1) {
                    throw new IllegalArgumentException("GPX documents with multiple tracks not supported yet.");
                }
                List<Observation> inputGPXEntries = gpx.trk.get(0).getEntries();
                MatchResult mr = mapMatching.match(inputGPXEntries, false);
                System.out.println(String.format("\tmatches: %d, GPX entries: %d", mr.getEdgeMatches().size(), inputGPXEntries.size()));
                System.out.println(String.format("\tGPX length: %f vs. %f", mr.getGpxEntriesLength(), mr.getMatchLength()));

                String outFile = f.toString() + ".res.gpx";
                System.out.println("\texport results to:" + outFile);

                ResponsePath responsePath = new PathMerger(mr.getGraph(), weighting).
                    doWork(PointList.EMPTY, Collections.singletonList(mr.getMergedPath()), hopper.getEncodingManager(), tr);
                if (responsePath.hasErrors()) {
                    System.err.println("Problem with file " + f + ", " + responsePath.getErrors());
                    continue;
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                    // The GPX output is not exactly the same as upstream GraphHopper.
                    // Upstream GraphHopper writes the timestamp of the first trackpoint of the input
                    // file to the metadata section of the output file. We don't do this because this
                    // is special to GPX. The same applies tothe name field of the metadata section.
                    //TODO If elevation support is added, remove hardcoded false here.
                    long time = System.currentTimeMillis();
                    String trackName = gpx.trk.get(0).name != null ? gpx.trk.get(0).name : "";
                    writer.append(GpxFromInstructions.createGPX(responsePath.getInstructions(),
                            trackName, time, hopper.hasElevation(), withRoute, true, false,
                            Constants.VERSION, tr));
                }
            } catch (IOException e) {
                System.out.println(String.format("Received IOException while reading GPX file %s from input stream: %s",
                        f.toString(), e.toString()));
            }
        }
        hopper.close();
    }
}
