/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package bef.rest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.List;

interface BefrestInternal {
    int LOG_LEVEL_DEFAULT = Befrest.LOG_LEVEL_INFO;

    void setStartServiceAlarm();

    String getSubscribeUri();

    List<NameValuePair> getSubscribeHeaders();

    NameValuePair getAuthHeader();

    int getSendOnAuthorizeBroadcastDelay();

    void sendBefrestBroadcast(Context context, int type, Bundle extras);

    void reportOnClose(Context context, int code);

    void reportOnOpen(Context context);

    class Util {
        private static final String TAG = "Util";
        protected static final String KEY_MESSAGE_PASSED = "KEY_MESSAGE_PASSED";
        private static final String BROADCAST_SENDING_PERMISSION_POSTFIX = ".permission.PUSH_SERVICE";
        static final int API_VERSION = 1;
        static final int SDK_VERSION = 2;

        /**
         * Is device connected to Internet?
         */
        @SuppressLint("MissingPermission")
        static boolean isConnectedToInternet(Context context) {
            try {
                ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();
                if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                    return true;
                }

                netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                } else {
                    netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                BefLog.e(TAG, e);
                return true;
            }
            return false;
        }

        static boolean isWifiConnected(Context context) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mWifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return mWifi.isConnected();
        }

        static String getBroadcastSendingPermission(Context context) {
            return context.getApplicationContext().getPackageName() + BROADCAST_SENDING_PERMISSION_POSTFIX;
        }

        static void disableConnectivityChangeListener(Context context) {
            ComponentName receiver = new ComponentName(context, BefrestConnectivityChangeReceiver.class);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            BefLog.v(TAG, "Befrest Connectivity change listener disabled");
        }

        static boolean customReceiverPass(Context context) throws PackageManager.NameNotFoundException {
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            Intent intent = new Intent();
            intent.setPackage(pInfo.packageName);
            //intent.setAction(Constants.ACTION_RECEIVE_MESSAGE);
            for (ResolveInfo resolveInfo : pm.queryBroadcastReceivers(intent,
                    PackageManager.GET_RESOLVED_FILTER |
                            PackageManager.GET_DISABLED_COMPONENTS)) {
                ActivityInfo activityInfo = resolveInfo.activityInfo;
                if (!activityInfo.packageName.equals(pInfo.packageName))
                    continue;
                IntentFilter filter = resolveInfo.filter;
                if (filter != null && filter.hasAction(NotificationReceiver.befrestPush)) {
                    boolean enabled = pm.getComponentEnabledSetting(new ComponentName(
                            activityInfo.packageName, activityInfo.name
                    )) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                    Log.i(TAG, "Handle receiver: " + activityInfo.name +
                            "; enabled: " + enabled);
                    return enabled;
                }
            }
            // Not found
            return true;
        }

        static void enableConnectivityChangeListener(Context context) {
            ComponentName receiver = new ComponentName(context, BefrestConnectivityChangeReceiver.class);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            BefLog.v(TAG, "Befrest Connectivity change listener enabled");
        }

        static String decodeBase64(String s) {
            try {
                return new String(Base64.decode(s, Base64.DEFAULT), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return "";
        }

        static int getLogLevel() {
            Befrest b = BefrestFactory.getInstanceIfExist();
            if (b != null) return b.getLogLevel();
            return LOG_LEVEL_DEFAULT;
        }

        static boolean isWakeLockPermissionGranted(Context context) {
            int res = context.checkCallingOrSelfPermission(Manifest.permission.WAKE_LOCK);
            return (res == PackageManager.PERMISSION_GRANTED);
        }
    }
}
