/*
 *  This file contains code of the GraphHopper project. See the
 *  THIRD_PARTY.md file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package de.geofabrik.railway_routing.http;

import java.util.ArrayList;

import com.graphhopper.config.Profile;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import static com.graphhopper.util.Parameters.Routing.U_TURN_COSTS;

public class RailwayImportCommand extends ConfiguredCommand<RailwayRoutingServerConfiguration> {

    public RailwayImportCommand() {
        super("import", "creates the graphhopper files used for later (faster) starts");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("datasource")
                .type(String.class)
                .required(true)
                .help("Input file in .osm.pbf format or .osm format.");
        subparser.addArgument("--graph_location")
                .type(String.class)
                .required(false)
                .help("Directory where to store the graph on disk.");
        subparser.addArgument("--flag_encoders")
                .type(String.class)
                .required(false)
                .help("Comma separated list of built-in flag encoders to be used.");
    }

    @Override
    protected void run(Bootstrap<RailwayRoutingServerConfiguration> bootstrap, Namespace args,
            RailwayRoutingServerConfiguration configuration) throws Exception {
        if (args.getString("datasource") != null) {
            configuration.getGraphHopperConfiguration().putObject("datareader.file", args.getString("datasource"));
        }
        if (args.getString("graph_location") != null) {
            configuration.getGraphHopperConfiguration().putObject("graph.location", args.getString("graph_location"));
        }
        String profiles = args.getString("flag_encoders");
        if (profiles != null) {
            String[] profileNames = profiles.split(",");
            ArrayList<Profile> profileList = new ArrayList<Profile>(profileNames.length);
            for (String p : profileNames) {
                profileList.add(new Profile(p)
                        .setVehicle(p)
                        .setWeighting("shortest")
                        .setTurnCosts(true)
                        //TODO Check if this way to set default u_turn_costs in seconds works.
                        .putHint(U_TURN_COSTS, 60 * 30)
                );
            }
            configuration.getGraphHopperConfiguration().setProfiles(profileList);
        }
        final RailwayRoutingManaged graphHopper = new RailwayRoutingManaged(configuration,
                bootstrap.getObjectMapper());
        graphHopper.getGraphHopper().importAndClose();
    }
}
