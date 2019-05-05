package bef.rest.befrest.utils;

class CustomTimeStamp {
    private long timeStamp;
    private String netWorkType;
    private String extraData;

    public CustomTimeStamp(long timeStamp, String netWorkType, String extraData) {
        this.timeStamp = timeStamp;
        this.netWorkType = netWorkType;
        this.extraData = extraData;
    }

}
