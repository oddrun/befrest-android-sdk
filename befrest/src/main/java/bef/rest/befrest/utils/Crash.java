package bef.rest.befrest.utils;

import java.util.ArrayList;
import java.util.List;

import bef.rest.befrest.Befrest;
import bef.rest.befrest.clientData.ClientData;

public class Crash extends Report {

    private String stackTrace;
    private List<CustomTimeStamp> ts;


    Crash(String stackTrace) {
        super(Befrest.getInstance().getBuildNumber(),Util.getDeviceInfo(),
                Befrest.getInstance().getContext().getPackageName(), SDKConst.SDK_INT,
                ClientData.getInstance().getChId(),ClientData.getInstance().getUId());
        this.stackTrace = stackTrace;
        ts = new ArrayList<>();
    }

    List<CustomTimeStamp> getTs() {
        return ts;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    void addNewTs(CustomTimeStamp customTimeStamp) {
        ts.add(customTimeStamp);
    }
}
