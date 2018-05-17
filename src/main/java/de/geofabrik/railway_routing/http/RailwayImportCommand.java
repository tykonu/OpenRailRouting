package de.geofabrik.railway_routing.http;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

public class RailwayImportCommand extends ConfiguredCommand<RailwayRoutingServerConfiguration> {

    public RailwayImportCommand() {
        super("import", "creates the graphhopper files used for later (faster) starts");
    }

    @Override
    protected void run(Bootstrap<RailwayRoutingServerConfiguration> bootstrap, Namespace namespace, RailwayRoutingServerConfiguration configuration) throws Exception {
        final RailwayRoutingManaged graphHopper = new RailwayRoutingManaged(configuration.getGraphHopperConfiguration());
        graphHopper.start();
        graphHopper.stop();
    }
}
