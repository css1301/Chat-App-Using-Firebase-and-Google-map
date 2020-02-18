package com.example.test.googlemap;

public class Destination {
    private double latitude;
    private double longitude;
    private String string_placeTitle;

    private String string_placeDesc;
    public Destination() {}

    public Destination(double latitude, double longitude, String string_placeDesc, String string_placeTitle) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.string_placeDesc = string_placeDesc;
        this.string_placeTitle = string_placeTitle;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    public String getString_placeTitle() {
        return string_placeTitle;
    }

    public void setString_placeTitle(String string_placeTitle) {
        this.string_placeTitle = string_placeTitle;
    }

    public String getString_placeDesc() {
        return string_placeDesc;
    }

    public void setString_placeDesc(String string_placeDesc) {
        this.string_placeDesc = string_placeDesc;
    }
}
