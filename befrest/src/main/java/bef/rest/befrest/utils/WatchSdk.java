package bef.rest.befrest.utils;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import static bef.rest.befrest.utils.BefrestPreferences.PREF_ANALYTICS;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_CRASH;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;
import static bef.rest.befrest.utils.Util.encodeToBase64;
import static bef.rest.befrest.utils.Util.md5;
import static bef.rest.befrest.utils.Util.netWorkType;
import static bef.rest.befrest.utils.Util.stackTraceToString;
import static bef.rest.befrest.utils.Util.toHexString;

public class WatchSdk {
    private static final String TAG = "WatchSdk";

    public static void reportAnalytics(AnalyticsType analyticsType, Object... o) {
        if (!isAllowedToCollect(analyticsType))
            return;
        switch (analyticsType) {
            case CONNECTION_LOST:
            case CANNOT_CONNECT:
                cacheAnalytics(new Analytics(analyticsType, 100), (String) o[1]);
                break;
            case INVALID_PONG:
                cacheAnalytics(new Analytics(analyticsType, 101), null);
                break;
            case NETWORK_DISCONNECTED:
                cacheAnalytics(new Analytics(analyticsType, 102), null);
                break;
            case TRY_TO_CONNECT:
                cacheAnalytics(new Analytics(analyticsType, 103), null);
                break;
            case BEFREST_CONNECTION_CHANGE:
                cacheAnalytics(new Analytics(analyticsType, 104), (String) o[0]);
                break;
            case RETRY:
                cacheAnalytics(new Analytics(analyticsType, 105), null);
                break;
            default:
                BefrestLog.w(TAG, "invalid Analytic type");
        }
    }

    public static void reportCrash(Exception e, String data) {
        try {
            if (!isAllowedToCollect(e))
                return;
            Map<String, Crash> crashMap = new HashMap<>();
            String currentStackTrace = encodeToBase64(stackTraceToString(e));
            String key = toHexString(md5(currentStackTrace).getBytes());
            Gson gson = new Gson();
            SharedPreferences sharedPrefs = getPrefs();
            if (sharedPrefs != null) {
                String stringCrash = sharedPrefs.getString(PREF_CRASH, "");
                if (stringCrash != null && !stringCrash.isEmpty()) {
                    Type type = new TypeToken<HashMap<String, Crash>>() {
                    }.getType();
                    crashMap = gson.fromJson(stringCrash, type);
                }
                Crash crash = crashMap.get(key);
                if (crash == null)
                    crash = new Crash(currentStackTrace);
                CustomTimeStamp ts = new CustomTimeStamp(System.currentTimeMillis(), netWorkType, data);
                crash.addNewTs(ts);
                crashMap.put(key, crash);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                stringCrash = gson.toJson(crashMap);
                editor.putString(PREF_CRASH, stringCrash);
                editor.apply();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private static void cacheAnalytics(Analytics analytics, String extraParam) {
        try {
            Map<AnalyticsType, Analytics> analyticsList = new HashMap<>();
            SharedPreferences sharedPrefs = getPrefs();
            Gson gson = new Gson();
            if (sharedPrefs != null) {
                String json = sharedPrefs.getString(PREF_ANALYTICS, "");
                if (json != null && !json.isEmpty()) {
                    Type type = new TypeToken<HashMap<AnalyticsType, Analytics>>() {
                    }.getType();
                    analyticsList = gson.fromJson(json, type);
                }
                Analytics currentAnalyticData = analyticsList.get(analytics.getAnalyticsType());
                CustomTimeStamp customTimeStamp = new CustomTimeStamp(System.currentTimeMillis(),
                        Util.netWorkType, extraParam);

                if (currentAnalyticData != null) {
                    currentAnalyticData.addNewTimeStamp(customTimeStamp);
                    analyticsList.put(analytics.getAnalyticsType(), currentAnalyticData);
                } else {
                    analytics.addNewTimeStamp(customTimeStamp);
                    analyticsList.put(analytics.getAnalyticsType(), analytics);
                }

                SharedPreferences.Editor editor = sharedPrefs.edit();
                json = gson.toJson(analyticsList);
                editor.putString(PREF_ANALYTICS, json);
                editor.apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isAllowedToCollect(Exception e) {
        if (getPrefs() != null) {
            String exceptionSimpleName = e.getClass().getSimpleName();
            return getPrefs().getBoolean(exceptionSimpleName, false);
        }
        return false;
    }

    private static boolean isAllowedToCollect(AnalyticsType analyticsType) {
        if (getPrefs() != null)
            return getPrefs().getBoolean(analyticsType.name(), false);
        return false;
    }
}
