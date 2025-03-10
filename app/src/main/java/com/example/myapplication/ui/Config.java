package com.example.myapplication.ui;

public class Config {
    private String stt = "";
    private String serial = "";
    private String staging = "1";
    private String errQI = "0";
    private String errQII = "0";
    private String errQIII = "0";
    private String errQ3 = "0";
    private String type = "Kiểm";
    private String tai = "QIII";
    private String ssDhm = "0";
    private int saturation = 50 ;
    private double roundOld = 0;
    private double falseValueMeterOld = 0;
    private double ratioOld = 0;
    private double correctionOld = 0;

    private double round = 0;
    private boolean isConnect = false;
    private boolean isStart = false;
    private double valueMau = 0;
    private double falseValueMeter = 0;
    private double ratio = 0;
    private double correction = 0;
    private double previousAngle = 0; // Lưu góc quay trước đó
    private double totalRotation = 0; // Tổng góc quay (tính cả phần thập phân)
    private double angleDifference = -1;
    private double angel = -1;
    private double angelStart = -1;
    // Getter and Setter methods for String variables
    public int getSaturation() {
        return saturation;
    }
    public void setSaturation(int saturation){
        this.saturation = saturation;
    }

    public String getStt() {
        return stt;
    }

    public void setStt(String stt) {
        this.stt = stt;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getStaging() {
        return staging;
    }

    public void setStaging(String staging) {
        this.staging = staging;
    }

    public String getErrQI() {
        return errQI;
    }

    public void setErrQI(String errQI) {
        this.errQI = errQI;
    }

    public String getErrQII() {
        return errQII;
    }

    public void setErrQII(String errQII) {
        this.errQII = errQII;
    }

    public String getErrQIII() {
        return errQIII;
    }

    public void setErrQIII(String errQIII) {
        this.errQIII = errQIII;
    }

    public String getErrQ3() {
        return errQ3;
    }

    public void setErrQ3(String errQ3) {
        this.errQ3 = errQ3;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTai() {
        return tai;
    }

    public void setTai(String tai) {
        this.tai = tai;
    }

    public String getSsDhm() {
        return ssDhm;
    }

    public void setSsDhm(String ssDhm) {
        this.ssDhm = ssDhm;
    }

    // Getter and Setter methods for boolean variables

    public boolean getIsConnect() {
        return isConnect;
    }

    public boolean getIsStart() {
        return isStart;
    }

    public void setConnect(boolean isConnect) {
        this.isConnect = isConnect;
    }

    public void setStart(boolean isStart) {
        this.isStart = isStart;
    }

    // Getter and Setter methods for double variables

    public double getValueMau() {
        return valueMau;
    }

    public void setValueMau(double falseMau) {
        this.valueMau = falseMau;
    }

    public double getFalseValueMeter() {
        return falseValueMeter;
    }

    public void setFalseValueMeter (double falseValueMeter){
        this.falseValueMeter  = falseValueMeter;
    }


    public double getRatio() {
        return ratio;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }



    public double getCorrection() {
        return correction;
    }

    public void setCorrection(double correction) {
        this.correction = correction;
    }
    public double getRound (){
        return round;
    }
    public  void setRound (double round){
        this.round = round;
    }
    public double getPreviousAngle() {
        return previousAngle;
    }

    public void setPreviousAngle(double previousAngle) {
        this.previousAngle = previousAngle;
    }

    // Getter và Setter cho totalRotation
    public double getTotalRotation() {
        return totalRotation;
    }

    public void setTotalRotation(double totalRotation) {
        this.totalRotation = totalRotation;
    }

    // Getter và Setter cho angleDifference
    public double getAngleDifference() {
        return angleDifference;
    }

    public void setAngleDifference(double angleDifference) {
        this.angleDifference = angleDifference;
    }

    // Getter và Setter cho angel
    public double getAngel() {
        return angel;
    }

    public void setAngel(double angel) {
        this.angel = angel;
    }

    // Getter và Setter cho angelStart
    public double getAngelStart() {
        return angelStart;
    }

    public void setAngelStart(double angelStart) {
        this.angelStart = angelStart;
    }

    public double getRoundOld() {
        return roundOld;
    }

    public void setRoundOld(double roundOld) {
        this.roundOld = roundOld;
    }

    public double getFalseValueMeterOld() {
        return falseValueMeterOld;
    }

    public void setFalseValueMeterOld(double falseValueMeterOld) {
        this.falseValueMeterOld = falseValueMeterOld;
    }

    public double getRatioOld() {
        return ratioOld;
    }

    public void setRatioOld(double ratioOld) {
        this.ratioOld = ratioOld;
    }

    public double getCorrectionOld() {
        return correctionOld;
    }

    public void setCorrectionOld(double correctionOld) {
        this.correctionOld = correctionOld;
    }
}
