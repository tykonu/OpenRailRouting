package de.geofabrik.railway_routing;


import static com.graphhopper.util.Helper.getMemInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.hppc.IntScatterSet;
import com.carrotsearch.hppc.IntSet;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import de.geofabrik.railway_routing.http.FlagEncoderConfiguration;

public class RailwayHopper extends GraphHopperOSM {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    /** set of internal node IDs which are tagged with railway=railway_crossing in OSM */
    private IntSet crossingsSet = null;

    class EncoderEncodedValue {
        private AbstractFlagEncoder encoder;
        private DecimalEncodedValue encodedValue;

        public EncoderEncodedValue(AbstractFlagEncoder encoder) {
            this.encoder = encoder;
            this.encodedValue = encoder.getDecimalEncodedValue(TurnCost.key(encoder.toString()));
        }

        public AbstractFlagEncoder getEncoder() {
            return encoder;
        }

        public DecimalEncodedValue getEncodedValue() {
            return encodedValue;
        }
    }

    public RailwayHopper() {
    }

    public void setCustomEncoderConfigs(List<FlagEncoderConfiguration> customEncoderConfigs) {
        setFlagEncoderFactory(new RailFlagEncoderFactory(customEncoderConfigs));
    }

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        crossingsSet = new IntScatterSet();
        OSMReader reader = new OSMReader(ghStorage);
        CrossingsSetHook hook = new CrossingsSetHook(reader, crossingsSet);
        reader.register(hook);
        return initDataReader(reader);
    }

    @Override
    protected DataReader importData() throws IOException {
        ensureWriteAccess();
        if (getGraphHopperStorage() == null)
            throw new IllegalStateException("Load graph before importing OSM data");

        if (getDataReaderFile() == null)
            throw new IllegalStateException("Couldn't load from existing folder: " + getGraphHopperLocation()
                    + " but also cannot use file for DataReader as it wasn't specified!");

        DataReader reader = createReader(getGraphHopperStorage());
        logger.info("using " + getGraphHopperStorage().toString() + ", memory:" + getMemInfo());
        reader.readGraph();
        return reader;
    }

    private double getAngle(double or1, double or2) {
        return Math.abs(or1 - or2);
    }

    private void addTurnCosts(TurnCostStorage tcs, List<EncoderEncodedValue> encodedValues, int fromEdge,
            int viaNode, int toEdge, double angleDiff, boolean crossing) {
        boolean forbiddenTurn = false;
        if (crossing && (angleDiff < 0.92 * Math.PI || angleDiff > 1.08 * Math.PI)) {
            forbiddenTurn = true;
        } else if (crossing) {
            return;
        }
        if (angleDiff < 0.3 * Math.PI || angleDiff > 1.7 * Math.PI) {
            // Disable this turn because it requires a change of direction.
            // Trains should run until the end of the edge before they reverse.
            forbiddenTurn = true;
        } else if (angleDiff < 0.75 * Math.PI || angleDiff > 1.25 * Math.PI) {
            forbiddenTurn = true;
        }
        if (!forbiddenTurn) {
            return;
        }
        for (EncoderEncodedValue ev : encodedValues) {
            if (!ev.getEncoder().supportsTurnCosts()) {
                continue;
            }
            tcs.set(ev.getEncodedValue(), fromEdge, viaNode, toEdge, Double.POSITIVE_INFINITY);
            tcs.set(ev.getEncodedValue(), toEdge, viaNode, fromEdge, Double.POSITIVE_INFINITY);
        }
    }

    private void handleSwitch(EdgeIterator iter, AngleCalc angleCalc, GraphHopperStorage ghs,
            List<EncoderEncodedValue> encodedValues, int node) {
        TurnCostStorage tcs = ghs.getTurnCostStorage();
        // get list of IDs of edges
        ArrayList<Integer> edges = new ArrayList<Integer>();
        ArrayList<Integer> adjNodes = new ArrayList<Integer>();
        // check if it is a railway crossing
        // We only get tower nodes here.
        int id = OSMReader.towerIdToMapId(node);
        boolean crossing = crossingsSet.contains(id);
        while (iter.next()) {
            edges.add(iter.getEdge());
            adjNodes.add(iter.getAdjNode());
        }
        for (int i = 0; i < adjNodes.size(); ++i) {
            EdgeIteratorState fromEdge = ghs.getEdgeIteratorState(edges.get(i), adjNodes.get(i));
            PointList fromPoints = fromEdge.fetchWayGeometry(FetchMode.ALL);
            double fromLon = fromPoints.getLon(1);
            double fromLat = fromPoints.getLat(1);
            double centreLon = fromPoints.getLon(0);
            double centreLat = fromPoints.getLat(0);
            double fromOrientation = angleCalc.calcOrientation(centreLat, centreLon, fromLat, fromLon);
            for (int j = i + 1; j < adjNodes.size(); ++j) {
                EdgeIteratorState toEdge = ghs.getEdgeIteratorState(edges.get(j), adjNodes.get(j));
                PointList toPoints = toEdge.fetchWayGeometry(FetchMode.ALL);
                double toLon = toPoints.getLon(1);
                double toLat = toPoints.getLat(1);
                double toOrientation = angleCalc.calcOrientation(centreLat, centreLon, toLat, toLon);
                double diff = getAngle(fromOrientation, toOrientation);
                addTurnCosts(tcs, encodedValues, fromEdge.getEdge(), node, toEdge.getEdge(), diff, crossing);
            }
        }
    }

    @Override
    protected void cleanUp() {
        super.cleanUp();
        AngleCalc angleCalc = new AngleCalc();
        GraphHopperStorage ghs = getGraphHopperStorage();
        EdgeExplorer explorer = ghs.createEdgeExplorer();

        // iterate over all nodes
        int nodes = ghs.getNodes();
        List<EncoderEncodedValue> encodedValues = new ArrayList<EncoderEncodedValue>(getEncodingManager().fetchEdgeEncoders().size());
        for (FlagEncoder encoder : getEncodingManager().fetchEdgeEncoders()) {
            encodedValues.add(new EncoderEncodedValue((AbstractFlagEncoder) encoder));
        }
        for (int start = 0; start < nodes; start++) {
            if (ghs.isNodeRemoved(start)) {
                continue;
            }
            EdgeIterator iter = explorer.setBaseNode(start);
            handleSwitch(iter, angleCalc, ghs, encodedValues, start);
        }
    }
}
