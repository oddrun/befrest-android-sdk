package bef.rest.befrest.utils;

import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import bef.rest.befrest.befrest.Befrest;

public class Crash extends Report {
    private String stackTrace;
    private String data;
    private List<Long> ts;

    public Crash(String stackTrace) {
        super(Befrest.getInstance().getBuildNumber(),Util.netWorkType,Util.getDeviceInfo(),
                Befrest.getInstance().getContext().getPackageName(),SDKConst.SDK_INT);
        this.stackTrace = stackTrace;
        ts = new ArrayList<>();
    }

    public Crash(String stackTrace, String data) {
        this(stackTrace);
        this.data = data;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public void addNewTs(){
        ts.add(System.currentTimeMillis());
    }

    private String encodeToBase64(String s){
        try {
            byte[] data = s.getBytes("UTF-8");
            return Base64.encodeToString(data, Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
           return "";
        }
    }
}
