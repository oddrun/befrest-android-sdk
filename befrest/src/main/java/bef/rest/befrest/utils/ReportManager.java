package bef.rest.befrest.utils;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bef.rest.befrest.utils.BefrestPreferences.PREF_ANALYTICS;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_CACHE_LIFE_TIME;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_CRASH;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_LIVE_CACHE;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;

public class ReportManager extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "ReportManager";
    private Map<String, Crash> crashMap;
    private Gson gson = new Gson();
    private Map<AnalyticsType, Analytics> analyticMap;
    private JsonObject analyticJson;
    private JsonObject crashJson;

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                long liveCacheData = prefs.getLong(PREF_LIVE_CACHE, 0);
                if (liveCacheData == 0) {
                    prefs.edit().putLong(PREF_LIVE_CACHE, System.currentTimeMillis()).apply();
                    BefrestLog.d(TAG, "set cache lifetime for analytics and crash");
                    return null;
                }
                loadCrashAndAnalyticToMap(prefs);
                if ((System.currentTimeMillis() - liveCacheData) <= loadCacheLifeTime(prefs)) {
                    if (isQueueLengthMoreThan20())
                        sendToServer();
                } else
                    sendToServer();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private long loadCacheLifeTime(SharedPreferences prefs) {
        //2 day default
        return prefs.getLong(PREF_CACHE_LIFE_TIME, 172_800_000);
    }

    private void loadCrashAndAnalyticToMap(SharedPreferences sharedPrefs) {
        BefrestLog.i(TAG, "load crash and analytic data to send server");
        crashMap = new HashMap<>();
        String cache = sharedPrefs.getString(PREF_CRASH, "");
        if (cache != null && !cache.isEmpty()) {
            Type type = new TypeToken<HashMap<String, Crash>>() {
            }.getType();
            crashMap = gson.fromJson(cache, type);
        }
        analyticMap = new HashMap<>();
        cache = sharedPrefs.getString(PREF_ANALYTICS, "");
        if (cache != null && !cache.isEmpty()) {
            Type type = new TypeToken<HashMap<AnalyticsType, Analytics>>() {
            }.getType();
            analyticMap = gson.fromJson(cache, type);
        }
    }

    private void clearAnalyticCache() {
        if (getPrefs() != null) {
            getPrefs().edit().putString(PREF_ANALYTICS, "").apply();
        }
    }

    private void clearCrashCache() {
        if (getPrefs() != null) {
            getPrefs().edit().putString(PREF_CRASH, "").apply();
        }
    }

    private void sendToServer() throws JSONException {
        prepareData();
        String url = NetworkManager.getInstance().generateReportUrl();
        String analyticResponse = NetworkManager.getInstance().
                getResponseFromUrl(url, SDKConst.PUT_REQUEST, null, null, analyticJson.toString());
        String crashResponse = NetworkManager.getInstance().
                getResponseFromUrl(url, SDKConst.PUT_REQUEST, null, null, crashJson.toString());
        if (analyticResponse != null) {
            if (new JSONObject(analyticResponse).getInt("errorCode") == 0)
                clearAnalyticCache();
            if (new JSONObject(crashResponse).getInt("errorCode") == 0)
                clearCrashCache();
        }
    }

    private void prepareData() {
        List<Analytics> analyticsList = new ArrayList<>();
        List<Crash> crashList = new ArrayList<>();
        for (Map.Entry<AnalyticsType, Analytics> entry : analyticMap.entrySet())
            analyticsList.add(entry.getValue());
        JsonArray analytic = gson.toJsonTree(analyticsList).getAsJsonArray();
        analyticJson = new JsonObject();
        analyticJson.add("analytic", analytic);
        for (Map.Entry<String, Crash> entry : crashMap.entrySet())
            crashList.add(entry.getValue());
        JsonArray crash = gson.toJsonTree(crashList).getAsJsonArray();
        crashJson = new JsonObject();
        crashJson.add("crash", crash);
    }

    private boolean isQueueLengthMoreThan20() {
        for (Map.Entry<String, Crash> entry : crashMap.entrySet())
            if (entry.getValue() != null && entry.getValue().getTs().size() > 20)
                return true;

        for (Map.Entry<AnalyticsType, Analytics> entry : analyticMap.entrySet())
            if (entry.getValue() != null && entry.getValue().getTs().size() > 20)
                return true;
        return false;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
    }
}
