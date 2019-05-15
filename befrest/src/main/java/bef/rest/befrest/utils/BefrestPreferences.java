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

import bef.rest.befrest.Befrest;

public class BefrestPreferences {
    public static final String PREF_U_ID = "PREF_U_ID";
    public static final String PREF_AUTH = "PREF_AUTH";
    public static final String PREF_CH_ID = "PREF_CH_ID";
    static final String PREF_LAST_RECEIVED_MESSAGES = "PREF_LAST_RECEIVED_MESSAGES";
    public static final String PREF_TOPIC= "PREF_TOPIC";
    static final String PREF_LIVE_CACHE = "PREF_LIVE_CACHE";
    static final String PREF_CRASH = "PREF_CRASH";
    static final String PREF_ANALYTICS = "PREF_ANALYTIC";
    public static final String PREF_PING_INTERVAL = "pingInterval";
    public static final String PREF_PING_TIMEOUT = "pingTimeOut";
    public static final String PREF_HANDSHAKE_TIMEOUT = "handshakeTimeOut";
    public static final String PREF_CACHE_LIFE_TIME = "cacheLifeTime";

    public static SharedPreferences getPrefs() {
        String SHARED_PREFERENCES_NAME = "rest.bef.SHARED_PREFERENCES";
        if (Befrest.getInstance().isBefrestInitialized())
            return Befrest.getInstance().getContext().
                    getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return null;
    }

    public static void saveInt(String key, int value) {
        if (getPrefs() == null)
            return;
        Editor editor = getPrefs().edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static void saveString(String key, String value) {
        if (getPrefs() == null)
            return;
        Editor editor = getPrefs().edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static void saveBoolean(String key, boolean value) {
        if (getPrefs() == null)
            return;
        Editor editor = getPrefs().edit();
        editor.putBoolean(key, value);
        editor.apply();
    }
    public static void saveLong(String key, long value) {
        if (getPrefs() == null)
            return;
        Editor editor = getPrefs().edit();
        editor.putLong(key, value);
        editor.apply();
    }
}