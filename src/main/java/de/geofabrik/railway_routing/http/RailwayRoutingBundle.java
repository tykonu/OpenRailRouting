/*
 *  This file contains code of the GraphHopper project. See the
 *  THIRD_PARTY.md file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package de.geofabrik.railway_routing.http;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.http.GHJerseyViolationExceptionMapper;
//import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.IllegalArgumentExceptionMapper;
import com.graphhopper.http.MultiExceptionGPXMessageBodyWriter;
import com.graphhopper.http.MultiExceptionMapper;
import com.graphhopper.http.TypeGPXFilter;
import com.graphhopper.http.health.GraphHopperHealthCheck;
import com.graphhopper.jackson.GraphHopperConfigModule;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.resources.CustomWeightingRouteResource;
import com.graphhopper.resources.I18NResource;
import com.graphhopper.resources.InfoResource;
import com.graphhopper.resources.MVTResource;
import com.graphhopper.resources.NearestResource;
import com.graphhopper.resources.RouteResource;
import com.graphhopper.resources.SPTResource;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.TranslationMap;

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class RailwayRoutingBundle implements ConfiguredBundle<RailwayRoutingServerConfiguration> {

    static class TranslationMapFactory implements Factory<TranslationMap> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public TranslationMap provide() {
            return graphHopper.getTranslationMap();
        }

        @Override
        public void dispose(TranslationMap instance) {

        }
    }

    static class GraphHopperStorageFactory implements Factory<GraphHopperStorage> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public GraphHopperStorage provide() {
            return graphHopper.getGraphHopperStorage();
        }

        @Override
        public void dispose(GraphHopperStorage instance) {

        }
    }

    static class EncodingManagerFactory implements Factory<EncodingManager> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public EncodingManager provide() {
            return graphHopper.getEncodingManager();
        }

        @Override
        public void dispose(EncodingManager instance) {

        }
    }

    static class LocationIndexFactory implements Factory<LocationIndex> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public LocationIndex provide() {
            return graphHopper.getLocationIndex();
        }

        @Override
        public void dispose(LocationIndex instance) {

        }
    }

    static class ProfileResolverFactory implements Factory<ProfileResolver> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public ProfileResolver provide() {
            return new ProfileResolver(graphHopper.getEncodingManager(),
                    graphHopper.getProfiles(),
                    graphHopper.getCHPreparationHandler().getCHProfiles(),
                    graphHopper.getLMPreparationHandler().getLMProfiles()
            );
        }

        @Override
        public void dispose(ProfileResolver profileResolver) {

        }
    }

    static class HasElevation implements Factory<Boolean> {

        @Inject
        GraphHopper graphHopper;

        @Override
        public Boolean provide() {
            return graphHopper.hasElevation();
        }

        @Override
        public void dispose(Boolean instance) {

        }
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // See #1440: avoids warning regarding com.fasterxml.jackson.module.afterburner.util.MyClassLoader
        bootstrap.setObjectMapper(io.dropwizard.jackson.Jackson.newMinimalObjectMapper());
        // avoids warning regarding com.fasterxml.jackson.databind.util.ClassUtil
        bootstrap.getObjectMapper().registerModule(new Jdk8Module());

        Jackson.initObjectMapper(bootstrap.getObjectMapper());
        bootstrap.getObjectMapper().registerModule(new GraphHopperConfigModule());
        bootstrap.getObjectMapper().setDateFormat(new StdDateFormat());
        // See https://github.com/dropwizard/dropwizard/issues/1558
        bootstrap.getObjectMapper().enable(MapperFeature.ALLOW_EXPLICIT_PROPERTY_RENAMING);
    }

    @Override
    public void run(RailwayRoutingServerConfiguration configuration, Environment environment) {
        for (Object k : System.getProperties().keySet()) {
            if (k instanceof String && ((String) k).startsWith("graphhopper."))
                throw new IllegalArgumentException("You need to prefix system parameters with '-Ddw.graphhopper.' instead of '-Dgraphhopper.' see #1879 and #1897");
        }
        for (Object k : System.getProperties().keySet()) {
            if (k instanceof String && ((String) k).startsWith("dw.graphhopper")) {
                String key = (String) k;
                int idx = key.indexOf("dw.graphhopper");
                String subkey = key.substring(15);
                if (idx == 0 && configuration.getGraphHopperConfiguration().getString(subkey, null) == null) {
                    throw new IllegalArgumentException("You defined the system property '-D" + key
                            + "' but '" + subkey + "' is not set in the " +
                            "configuration. At least, you need to set it to an empty string in " + 
                            "the configuration file.");
                }
            }
        }

        // When Dropwizard's Hibernate Validation misvalidates a query parameter,
        // a JerseyViolationException is thrown.
        // With this mapper, we use our custom format for that (backwards compatibility),
        // and also coerce the media type of the response to JSON, so we can return JSON error
        // messages from methods that normally have a different return type.
        // That's questionable, but on the other hand, Dropwizard itself does the same thing,
        // not here, but in a different place (the custom parameter parsers).
        // So for the moment we have to assume that both mechanisms
        // a) always return JSON error messages, and
        // b) there's no need to annotate the method with media type JSON for that.
        //
        // However, for places that throw IllegalArgumentException or MultiException,
        // we DO need to use the media type JSON annotation, because
        // those are agnostic to the media type (could be GPX!), so the server needs to know
        // that a JSON error response is supported. (See below.)
        environment.jersey().register(new GHJerseyViolationExceptionMapper());

        // If the "?type=gpx" parameter is present, sets a corresponding media type header
        environment.jersey().register(new TypeGPXFilter());

        // Together, these two take care that MultiExceptions thrown from RouteResource
        // come out as JSON or GPX, depending on the media type
        environment.jersey().register(new MultiExceptionMapper());
        environment.jersey().register(new MultiExceptionGPXMessageBodyWriter());

        // This makes an IllegalArgumentException come out as a MultiException with
        // a single entry.
        environment.jersey().register(new IllegalArgumentExceptionMapper());

        final RailwayRoutingManaged graphHopperManaged = new RailwayRoutingManaged(
                configuration, environment.getObjectMapper());
        environment.lifecycle().manage(graphHopperManaged);
        final GraphHopper graphHopper = graphHopperManaged.getGraphHopper();
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(configuration.getGraphHopperConfiguration()).to(GraphHopperConfig.class);
                bind(graphHopper).to(GraphHopper.class);
                bind(graphHopper).to(GraphHopperAPI.class);

                bindFactory(ProfileResolverFactory.class).to(ProfileResolver.class);
                bindFactory(HasElevation.class).to(Boolean.class).named("hasElevation");
                bindFactory(LocationIndexFactory.class).to(LocationIndex.class);
                bindFactory(TranslationMapFactory.class).to(TranslationMap.class);
                bindFactory(EncodingManagerFactory.class).to(EncodingManager.class);
                bindFactory(GraphHopperStorageFactory.class).to(GraphHopperStorage.class);
            }
        });

        environment.jersey().register(MVTResource.class);
        environment.jersey().register(NearestResource.class);
        environment.jersey().register(RouteResource.class);
        environment.jersey().register(CustomWeightingRouteResource.class);
        environment.jersey().register(MatchResource.class);
        environment.jersey().register(SPTResource.class);
        environment.jersey().register(I18NResource.class);
        environment.jersey().register(InfoResource.class);

        environment.healthChecks().register("graphhopper", new GraphHopperHealthCheck(graphHopperManaged.getGraphHopper()));
    }
}
