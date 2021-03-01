/*
 *  This file contains code of the GraphHopper project. See the
 *  THIRD_PARTY.md file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package de.geofabrik.railway_routing.http;

import java.util.Map;
import java.util.EnumSet;
import java.util.HashMap;

import javax.servlet.DispatcherType;


import com.graphhopper.http.resources.RootResource;
import com.graphhopper.http.CORSFilter;

import io.dropwizard.Application;
import io.dropwizard.bundles.assets.ConfiguredAssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public final class RailwayRoutingApplication extends Application<RailwayRoutingServerConfiguration> {

    public static void main(String[] args) throws Exception {
        new RailwayRoutingApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<RailwayRoutingServerConfiguration> bootstrap) {
        bootstrap.addBundle(new RailwayRoutingBundle());
        Map<String, String> resourceToURIMappings = new HashMap<>();
        resourceToURIMappings.put("/assets/", "/maps/");
        resourceToURIMappings.put("/map-matching-frontend/", "/map-matching/");
        resourceToURIMappings.put("/META-INF/resources/webjars", "/webjars"); // https://www.webjars.org/documentation#dropwizard
        bootstrap.addCommand(new RailwayImportCommand());
        bootstrap.addCommand(new RailwayMatchCommand());
        bootstrap.addBundle(new ConfiguredAssetsBundle(resourceToURIMappings, "index.html"));
    }

    @Override
    public void run(RailwayRoutingServerConfiguration configuration, Environment environment) throws Exception {

        environment.jersey().register(new RootResource());
        environment.servlets().addFilter("cors", CORSFilter.class).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");

    }
}
