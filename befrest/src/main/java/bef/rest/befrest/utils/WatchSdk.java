package bef.rest.befrest.utils;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import bef.rest.befrest.befrest.Befrest;

import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;
import static bef.rest.befrest.utils.Util.stackTraceToString;

public class WatchSdk {
    private static final String TAG = "WatchSdk";

    public static void reportAnalytics(AnalyticsType analyticsType, Object... o) {
        switch (analyticsType) {
            case CONNECTION_LOST:
            case CANNOT_CONNECT:
                cacheReport(new Analytics(analyticsType, (String) o[1], (int) o[0]));
                break;
            case INVALID_PONG:
                cacheReport(new Analytics(analyticsType, null, 100));
                break;
            case MALFORMED_DATA:
                cacheReport(new Analytics(analyticsType, ((Exception) o[0]).getMessage(), 10));
                break;
            case NETWORK_CONNECTED:
                cacheReport(new Analytics(analyticsType, null, 101));
                break;
            case NETWORK_DISCONNECTED:
                cacheReport(new Analytics(analyticsType, null, 102));
                break;
            case TRY_TO_CONNECT:
                cacheReport(new Analytics(analyticsType, null, 103));
                break;
            case BEFREST_CONNECTION_CHANGE:
                cacheReport(new Analytics(analyticsType, null, 104));
                break;
            case RETRY:
                cacheReport(new Analytics(analyticsType, null, (int) o[0]));
                break;
            default:
                BefrestLog.w(TAG, "invalid Analytic type");
        }
    }

    public static void reportCrash(Exception e, String data) {
        Map<String, Crash> crashMap;
        String currentStackTrace = stackTraceToString(e);
        Gson gson = new Gson();
        SharedPreferences sharedPrefs = getPrefs();
        if (sharedPrefs != null) {
            String stringCrash = sharedPrefs.getString("Crash", "");
            if (stringCrash != null && !stringCrash.isEmpty()) {
                Type type = new TypeToken<HashMap<String, Crash>>() {
                }.getType();
                crashMap = gson.fromJson(stringCrash, type);
                Crash crash = crashMap.get(currentStackTrace);
                if (crash == null) {
                    crash = new Crash(currentStackTrace,data);
                }
                crash.addNewTs();
                crashMap.put(currentStackTrace, crash);
            }
        }
    }

    private static void cacheReport(Analytics analytics) {
        Map<AnalyticsType, Analytics> analyticsList = new HashMap<>();
        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(Befrest.getInstance().getContext());
        Gson gson = new Gson();
        String json = sharedPrefs.getString("Analytics", "");
        if (json != null && !json.isEmpty()) {
            Type type = new TypeToken<HashMap<AnalyticsType, Analytics>>() {
            }.getType();
            analyticsList = gson.fromJson(json, type);
        }
        Analytics currentAnalyticData = analyticsList.get(analytics.getAnalyticsType());
        if (currentAnalyticData != null) {
            currentAnalyticData.addNewTimeStamp(System.currentTimeMillis());
            analyticsList.put(analytics.getAnalyticsType(), currentAnalyticData);
        } else {
            analytics.addNewTimeStamp(System.currentTimeMillis());
            analyticsList.put(analytics.getAnalyticsType(), analytics);
        }

        SharedPreferences.Editor editor = sharedPrefs.edit();
        json = gson.toJson(analyticsList);
        editor.putString("Analytics", json);
        editor.apply();
    }
}
