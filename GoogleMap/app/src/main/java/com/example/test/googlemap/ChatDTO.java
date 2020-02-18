package com.example.test.googlemap;

public class ChatDTO {
    private String userName;
    private String message;
    private double latitude;
    private double longitude;

    public ChatDTO() {}

    public ChatDTO(String userName, String message) {
        this.userName = userName;
        this.message = message;
    }
    public ChatDTO(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public ChatDTO(String userName, String message, double latitude, double longitude) {
        this.userName = userName;
        this.message = message;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUserName() {
        return userName;
    }

    public String getMessage() {
        return message;
    }
}
