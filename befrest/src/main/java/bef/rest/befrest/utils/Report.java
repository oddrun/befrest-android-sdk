package bef.rest.befrest.utils;

abstract class Report {
    private int buildNumber;
    private String deviceModel;
    private String pkg;
    private int apiVersion;
    private String chId;

    Report(int buildNumber,String deviceModel, String pkg, int apiVersion,String chId) {
        this.buildNumber = buildNumber;
        this.deviceModel = deviceModel;
        this.pkg = pkg;
        this.apiVersion = apiVersion;
        this.chId=chId;
    }
}
