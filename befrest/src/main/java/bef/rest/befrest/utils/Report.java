package bef.rest.befrest.utils;

class Report {
    private int buildNumber;
    private String deviceModel;
    private String pkg;
    private int apiVersion;
    private String chId;
    private String appId;

    Report(int buildNumber, String deviceModel, String pkg, int apiVersion, String chId, long uId) {
        this.buildNumber = buildNumber;
        this.deviceModel = deviceModel;
        this.pkg = pkg;
        this.apiVersion = apiVersion;
        this.chId = chId;
        this.appId = String.valueOf(uId);
    }
}
