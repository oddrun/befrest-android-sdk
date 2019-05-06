package bef.rest.befrest.utils;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
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
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            isQueueLengthMoreThanFive(prefs);
            long cacheLifeTime = prefs.getLong(PREF_CACHE_LIFE_TIME, 0);
            if (cacheLifeTime == 0) {
                prefs.edit().putLong(PREF_CACHE_LIFE_TIME, System.currentTimeMillis()).apply();
                BefrestLog.d(TAG, "set cache lifetime for analytics and crash");
                return null;
            } else {
                if ((System.currentTimeMillis() - cacheLifeTime) <= 172800000
                        && isQueueLengthMoreThanFive(prefs)) {

                } else {

                }
            }

        }
        return null;
    }

    /**
     * use for send to server
     */
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

    private boolean isQueueLengthMoreThanFive(SharedPreferences sharedPrefs) {
        String cache = sharedPrefs.getString(PREF_CRASH, "");
        Log.i(TAG, "isQueueLengthMoreThanFive: " + cache);
        if (cache != null && !cache.isEmpty()) {
            Type type = new TypeToken<HashMap<String, Crash>>() {
            }.getType();
            crashMap = gson.fromJson(cache, type);
            for (Map.Entry<String, Crash> entry : crashMap.entrySet())
                if (entry.getValue() != null && entry.getValue().getTs().size() > 20)
                    return true;
        }

        cache = sharedPrefs.getString(PREF_ANALYTICS, "");
        Log.i(TAG, "isQueueLengthMoreThanFive: analytic" + cache);
        if (cache != null && !cache.isEmpty()) {
            Type type = new TypeToken<HashMap<AnalyticsType, Analytics>>() {
            }.getType();
            analyticMap = gson.fromJson(cache, type);
            for (Map.Entry<AnalyticsType, Analytics> entry : analyticMap.entrySet())
                if (entry.getValue() != null && entry.getValue().getTs().size() > 20)
                    return true;
        }
        return false;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
    }
}
