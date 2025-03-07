package com.example.myapplication.ui;

public class SaveMessage {
    private String serial;
    private double correction;
    private String tai;
    private String type;

    private double ratio;
    private double round;

    private double falseValue;
    private double ssDhmau;
    private String timestamp;

    // Constructor, getters và setters
    public SaveMessage(String serial,double correction, String tai,String type, double round, double ratio, double falseValue, double ssDhmau, String timestamp) {
        this.serial = serial;
        this.correction = correction;
        this.tai = tai;
        this.type = type;
        this.round = round;
        this.ratio = ratio;
        this.falseValue = falseValue;
        this.ssDhmau = ssDhmau;
        this.timestamp = timestamp;
    }

    public String getSerial() {
        return serial;
    }
    public double getCorrection() {
        return correction;
    }
    public String getType(){
        return type;
    }
    public double getFalseValue() {
        return falseValue;
    }

    public double getRound() {
        return round;
    }

    public double getRatio() {
        return ratio;
    }

    public String getTai() {
        return tai;
    }

    public double getSsDhmau() {
        return ssDhmau;
    }

    public String getTimestamp() {
        return timestamp;
    }
}

