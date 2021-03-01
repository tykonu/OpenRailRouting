/*
 *  This file contains code of the GraphHopper project. See the
 *  THIRD_PARTY.md file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package de.geofabrik.railway_routing.http;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.geofabrik.railway_routing.RailwayHopper;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RailwayRoutingManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RailwayHopper graphHopper;

    @Inject
    public RailwayRoutingManaged(RailwayRoutingServerConfiguration configuration, ObjectMapper objectMapper) {
        ObjectMapper localObjectMapper = objectMapper.copy();
        localObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        graphHopper = (RailwayHopper) new RailwayHopper().forServer();
        graphHopper.setCustomEncoderConfigs(configuration.getFlagEncoderConfigurations());
        graphHopper.init(configuration.getGraphHopperConfiguration());
    }

    @Override
    public void start() throws Exception {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:" + graphHopper.getGraphHopperLocation()
                + ", data_reader_file:" + graphHopper.getDataReaderFile()
                + ", flag_encoders:" + graphHopper.getEncodingManager()
                + ", " + graphHopper.getGraphHopperStorage().toDetailsString());
    }

    RailwayHopper getGraphHopper() {
        return graphHopper;
    }


    @Override
    public void stop() throws Exception {
        graphHopper.close();
    }

}
