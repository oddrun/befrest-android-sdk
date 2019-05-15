package bef.rest.befrest.utils;

import java.util.ArrayList;
import java.util.List;

import bef.rest.befrest.Befrest;
import bef.rest.befrest.clientData.ClientData;

import static bef.rest.befrest.utils.SDKConst.SDK_INT;

public class Analytics extends Report {
    private AnalyticsType analyticsType;

    private int code;
    private List<CustomTimeStamp> ts = new ArrayList<>();

    Analytics(AnalyticsType analyticsType, int code) {
        super(Befrest.getInstance().getBuildNumber(), Util.getDeviceInfo(),
                Befrest.getInstance().getContext().getPackageName(), SDK_INT,
                ClientData.getInstance().getChId(),ClientData.getInstance().getUId());
        this.analyticsType = analyticsType;
        this.code = code;
    }

    AnalyticsType getAnalyticsType() {
        return analyticsType;
    }

    public int getCode() {
        return code;
    }

    List<CustomTimeStamp> getTs() {
        return ts;
    }

    void addNewTimeStamp(CustomTimeStamp tm) {
        ts.add(tm);
    }
}
