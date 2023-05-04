package com.uod.uodiotapp;

public class TelemetryDataModel {
    private double illuminance;

    public TelemetryDataModel(double illuminance) {
        this.illuminance = illuminance;
    }

    public double getIlluminance() {
        return illuminance;
    }

    public void setIlluminance(double illuminance) {
        this.illuminance = illuminance;
    }
}
