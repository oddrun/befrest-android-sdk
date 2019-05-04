package bef.rest.befrest.utils;

abstract class Report {
    private int buildNumber;
    private int netType;
    private String deviceModel;
    private String pkg;
    private int apiVersion;

    public Report(int buildNumber, int netType, String deviceModel, String pkg, int apiVersion) {
        this.buildNumber = buildNumber;
        this.netType = netType;
        this.deviceModel = deviceModel;
        this.pkg = pkg;
        this.apiVersion = apiVersion;
    }
}
