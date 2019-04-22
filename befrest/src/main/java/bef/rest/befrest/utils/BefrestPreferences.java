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
package bef.rest.befrest.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import bef.rest.befrest.befrest.Befrest;

public class BefrestPreferences {

    private static String SHARED_PREFERENCES_NAME = "rest.bef.SHARED_PREFERENCES";
    public static final String PREF_U_ID = "PREF_U_ID";
    private static final String PREF_AUTH = "PREF_AUTH";
    public static final String PREF_CH_ID = "PREF_CH_ID";
    private static final String PREF_SERVICE_CHOOSER = "PREF_SERVICE_CHOOSER";
    public static final String PREF_TOPICS = "PREF_TOPICS";
    static final String PREF_LOG_LEVEL = "PREF_LOG_LEVEL";
    public static final String PREF_CUSTOM_PUSH_SERVICE_NAME = "PREF_CUSTOM_PUSH_SERVICE_NAME";
    static final String PREF_LAST_RECEIVED_MESSAGES = "PREF_LAST_RECEIVED_MESSAGES";


    public static SharedPreferences getPrefs() {
        return Befrest.getInstance().getContext().
                getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static void saveInt(String key, int value) {
        Editor editor = getPrefs().edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static void saveString(String key, String value) {
        Editor editor = getPrefs().edit();
        editor.putString(key, value);
        editor.apply();
    }

    static void saveFloat(String key, float value) {
        Editor editor = getPrefs().edit();
        editor.putFloat(key, value);
        editor.apply();
    }

    static void saveBoolean(String key, boolean value) {
        Editor editor = getPrefs().edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    static void saveLong(String key, long value) {
        Editor editor = getPrefs().edit();
        editor.putLong(key, value);
        editor.apply();
    }

    static void saveToPrefs(long uId, String AUTH, String chId) {
        Editor prefEditor = Befrest.getInstance().getContext().
                getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
        prefEditor.putLong(PREF_U_ID, uId);
        prefEditor.putString(PREF_AUTH, AUTH);
        prefEditor.putString(PREF_CH_ID, chId);
        prefEditor.apply();
    }

    static void removePrefs() {
        Editor editor = getPrefs().edit();
        editor.clear();
        editor.apply();

    }

    /**
     * @param value save into sharedPreferences
     */
    public static void setRunningService(boolean value) {
        saveBoolean(PREF_SERVICE_CHOOSER, value);
    }

    /*
     * @return false if job scheduler is running
     */
    public static boolean getRunningService() {
        return getPrefs().getBoolean(PREF_SERVICE_CHOOSER, false);
    }

}