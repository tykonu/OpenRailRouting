package de.geofabrik.railway_routing;

import java.util.List;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.util.PMap;

import de.geofabrik.railway_routing.http.FlagEncoderConfiguration;

public class RailFlagEncoderFactory implements FlagEncoderFactory {

    List<FlagEncoderConfiguration> definedFlagEncoders;

    RailFlagEncoderFactory(List<FlagEncoderConfiguration> customEncoderConfigs) {
        definedFlagEncoders = customEncoderConfigs;
    }

    public PMap createFlagEncoderProperties(FlagEncoderConfiguration config) {
        PMap properties = new PMap();
        properties.putObject(RailFlagEncoder.NAME, config.getName());
        properties.putObject(RailFlagEncoder.RAILWAY, config.getRailway());
        properties.putObject(RailFlagEncoder.ELECTRIFIED, config.getElectrified());
        properties.putObject(RailFlagEncoder.VOLATAGES, config.getVoltages());
        properties.putObject(RailFlagEncoder.FREQUENCIES, config.getFrequencies());
        properties.putObject(RailFlagEncoder.GAUGES, config.getGauges());
        properties.putObject(RailFlagEncoder.MAXSPEED, config.getMaxspeed());
        properties.putObject(RailFlagEncoder.SPEED_FACTOR, config.getSpeedFactor());
        return properties;
    }

    public static String[] getKnownEncoderNames() {
        String[] names ={"freight_electric_15kvac_25kvac", "freight_diesel",
            "tgv_15kvac25kvac1.5kvdc", "tgv_25kvac1.5kvdc3kvdc", "freight_25kvac1.5kvdc3kvdc"};
        return names;
    }

    @Override
    public FlagEncoder createFlagEncoder(String name, PMap configuration) {
        for (FlagEncoderConfiguration c : definedFlagEncoders) {
            if (c.getName().equalsIgnoreCase(name)) {
                return new RailFlagEncoder(createFlagEncoderProperties(c));
            }
        }
        PMap properties = new PMap();
        properties.putObject(RailFlagEncoder.NAME, name);
        if (name.equals("freight_electric_15kvac_25kvac")) {
            properties.putObject(RailFlagEncoder.ELECTRIFIED, "contact_line");
            properties.putObject(RailFlagEncoder.VOLATAGES, "15000;25000");
            properties.putObject(RailFlagEncoder.FREQUENCIES, "16.7;16.67;50");
            properties.putObject(RailFlagEncoder.GAUGES, "1435");
            properties.putObject(RailFlagEncoder.MAXSPEED, 90);
        } else if (name.equals("freight_diesel")) {
            properties.putObject(RailFlagEncoder.ELECTRIFIED, "");
            properties.putObject(RailFlagEncoder.GAUGES, "1435");
            properties.putObject(RailFlagEncoder.MAXSPEED, 90);
        } else if (name.equals("tgv_15kvac25kvac1.5kvdc")) {
            properties.putObject(RailFlagEncoder.ELECTRIFIED, "contact_line");
            properties.putObject(RailFlagEncoder.VOLATAGES, "15000;25000;1500");
            properties.putObject(RailFlagEncoder.FREQUENCIES, "16.7;16.67;50;0");
            properties.putObject(RailFlagEncoder.GAUGES, "1435");
            properties.putObject(RailFlagEncoder.MAXSPEED, 319);
            properties.putObject(RailFlagEncoder.SPEED_FACTOR, 11);
        } else if (name.equals("tgv_25kvac1.5kvdc3kvdc")) {
            properties.putObject(RailFlagEncoder.ELECTRIFIED, "contact_line");
            properties.putObject(RailFlagEncoder.VOLATAGES, "25000;3000;1500");
            properties.putObject(RailFlagEncoder.FREQUENCIES, "0;50");
            properties.putObject(RailFlagEncoder.GAUGES, "1435");
            properties.putObject(RailFlagEncoder.MAXSPEED, 319);
            properties.putObject(RailFlagEncoder.SPEED_FACTOR, 11);
        } else if (name.equals("freight_25kvac1.5kvdc3kvdc")) {
            properties.putObject(RailFlagEncoder.ELECTRIFIED, "contact_line");
            properties.putObject(RailFlagEncoder.VOLATAGES, "25000;3000;1500");
            properties.putObject(RailFlagEncoder.FREQUENCIES, "0;50");
            properties.putObject(RailFlagEncoder.GAUGES, "1435");
            properties.putObject(RailFlagEncoder.MAXSPEED, 90);
        } else {
            throw new IllegalArgumentException("Flag encoder " + name + " not found.");
        }
        if (properties.isEmpty()) {
            return null;
        }
        return new RailFlagEncoder(properties);
    }
}
