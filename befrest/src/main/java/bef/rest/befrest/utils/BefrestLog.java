package bef.rest.befrest.utils;

import android.util.Log;

import bef.rest.befrest.clientData.ClientData;

public class BefrestLog {

    private static String TAG = "Befrest-[ ";

    private static int getLogLevel() {
        return ClientData.getInstance().getLogLevel();
    }

    public static void i(String tag, String message) {
        if (getLogLevel() < SDKConst.LOG_LEVEL_INFO)
            Log.i(TAG + tag + " ]", message+"");
    }

    public static void e(String tag, String message) {
        if (getLogLevel() <= SDKConst.LOG_LEVEL_ERROR)
            Log.e(TAG + tag + " ]", message+"");
    }

    public static void v(String tag, String message) {
        if (getLogLevel() <= SDKConst.LOG_LEVEL_VERBOSE)
            Log.v(TAG + tag + " ]", message+"");
    }

    public static void w(String tag, String message) {
        if (getLogLevel() <= SDKConst.LOG_LEVEL_WARN)
            Log.w(TAG + tag + " ]", message+"");
    }

    public static void d(String tag, String message) {
        if (getLogLevel() <= SDKConst.LOG_LEVEL_DEBUG)
            Log.d(TAG + tag + " ]", message+"");
    }

}
