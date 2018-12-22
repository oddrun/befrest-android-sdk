/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package bef.rest;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

class BefrestPrefrences {

    /**
     * Name for sharedPreferences used for saving BefrestImpl data.
     */
    static String SHARED_PREFERENCES_NAME = "rest.bef.SHARED_PREFERENCES";
    static final String PREF_U_ID = "PREF_U_ID";
    static final String PREF_AUTH = "PREF_AUTH";
    static final String PREF_CH_ID = "PREF_CH_ID";
    static final String PREF_LAST_STATE = "PREF_LAST_STATE";
    static final String PREF_TOPICS = "PREF_TOPICS";
    static final String PREF_LOG_LEVEL = "PREF_LOG_LEVEL";
    static final String PREF_CUSTOM_PUSH_SERVICE_NAME = "PREF_CUSTOM_PUSH_SERVICE_NAME";
    static final String PREF_CONTINUOUS_CLOSES = "PREF_CONTINUOUS_CLOSES";
    static final String PREF_CONTINUOUS_CLOSES_TYPES = "PREF_CONTINUOUS_CLOSES_TYPES";
    static final String PREF_LAST_SUCCESSFUL_CONNECT_TIME = "PREF_LAST_SUCCESSFUL_CONNECT_TIME";
    static final String PREF_CONNECT_ANOMALY_DATA_RECORDING_TIME = "PREF_CONNECT_ANOMALY_DATA_RECORDING_TIME";
    static final String PREF_LAST_RECEIVED_MESSAGES = "PREF_LAST_RECEIVED_MESSAGES";
    static final String PREF_FCM_TOKEN="PREF_FCM_TOKEN";

    static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    static void saveInt(Context context, String key, int value) {
        Editor editor = getPrefs(context).edit();
        editor.putInt(key, value);
        editor.apply();
    }

    static void saveString(Context context, String key, String value) {
        Editor editor = getPrefs(context).edit();
        editor.putString(key, value);
        editor.apply();
    }

    static void saveFloat(Context context, String key, float value) {
        Editor editor = getPrefs(context).edit();
        editor.putFloat(key, value);
        editor.apply();
    }

    static void saveBoolean(Context context, String key, boolean value) {
        Editor editor = getPrefs(context).edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    static void saveLong(Context context, String key, long value) {
        Editor editor = getPrefs(context).edit();
        editor.putLong(key, value);
        editor.apply();
    }

    static void saveToPrefs(Context context, long uId, String AUTH, String chId) {
        Editor prefEditor = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
        prefEditor.putLong(PREF_U_ID, uId);
        prefEditor.putString(PREF_AUTH, AUTH);
        prefEditor.putString(PREF_CH_ID, chId);
        prefEditor.apply();
    }
}