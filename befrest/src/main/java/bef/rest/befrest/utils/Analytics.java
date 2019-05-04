package bef.rest.befrest.utils;

import java.util.ArrayList;
import java.util.List;

import bef.rest.befrest.befrest.Befrest;

import static bef.rest.befrest.utils.SDKConst.SDK_INT;

public class Analytics extends Report {
    private AnalyticsType analyticsType;
    private String reason;
    private int code;
    private List<Long> timeStamp = new ArrayList<>();

    public Analytics() {
        super(Befrest.getInstance().getBuildNumber(),Util.netWorkType,Util.getDeviceInfo(),
                Befrest.getInstance().getContext().getPackageName(),SDK_INT);
    }

    Analytics(AnalyticsType analyticsType, String reason, int code) {
        super(Befrest.getInstance().getBuildNumber(),Util.netWorkType,Util.getDeviceInfo(),
                Befrest.getInstance().getContext().getPackageName(),SDK_INT);
        this.analyticsType = analyticsType;
        this.reason = reason;
        this.code = code;
    }

    AnalyticsType getAnalyticsType() {
        return analyticsType;
    }

    public String getReason() {
        return reason;
    }

    public int getCode() {
        return code;
    }

    public List<Long> getTimeStamp() {
        return timeStamp;
    }

    void addNewTimeStamp(Long tm){
        timeStamp.add(tm);
    }
}
