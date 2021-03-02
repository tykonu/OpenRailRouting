package de.geofabrik.railway_routing.http;


import javax.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FlagEncoderConfiguration {

    @NotEmpty
    private String name;

    private String railway = "rail";
    private String electrified = "";
    private String voltages = "";
    private String frequencies = "";
    private String gauges = "";
    private int speedFactor = 5;
    private int maxspeed = 90;
    private boolean yardSpur = true;

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public String getRailway() {
        return railway;
    }

    @JsonProperty
    public String getElectrified() {
        return electrified;
    }

    @JsonProperty
    public String getVoltages() {
        return voltages;
    }

    @JsonProperty
    public String getFrequencies() {
        return frequencies;
    }

    @JsonProperty
    public String getGauges() {
        return gauges;
    }

    @JsonProperty
    @JsonAlias("speedFactor")
    public int getSpeedFactor() {
        return speedFactor;
    }

    @JsonProperty
    public int getMaxspeed() {
        return maxspeed;
    }

    @JsonProperty
    public boolean getYardSpur() {
        return yardSpur;
    }


    @JsonProperty
    public void setName(String value) {
        name = value;
    }

    @JsonProperty
    public void setRailway(String value) {
        railway = value;
    }

    @JsonProperty
    public void setElectrified(String value) {
        electrified = value;
    }

    @JsonProperty
    public void setVoltages(String value) {
        voltages = value;
    }

    @JsonProperty
    public void setFrequencies(String value) {
        frequencies = value;
    }

    @JsonProperty
    public void setGauges(String value) {
        gauges = value;
    }

    @JsonProperty
    @JsonAlias("speedFactor")
    public void setSpeedFactor(int value) {
        speedFactor = value;
    }

    @JsonProperty
    public void setMaxspeed(int value) {
        maxspeed = value;
    }

    @JsonProperty
    public void setYardSpur(boolean value) {
        yardSpur = value;
    }
}
