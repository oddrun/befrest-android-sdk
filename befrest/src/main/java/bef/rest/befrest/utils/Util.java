package bef.rest.befrest.utils;

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
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Base64;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Comparator;

import bef.rest.befrest.befrest.Befrest;
import bef.rest.befrest.befrest.BefrestConnectivityChangeReceiver;
import bef.rest.befrest.befrest.BefrestMessage;

import static bef.rest.befrest.utils.SDKConst.BEFREST_CONNECTED;
import static bef.rest.befrest.utils.SDKConst.BROADCAST_SENDING_PERMISSION_POSTFIX;
import static bef.rest.befrest.utils.SDKConst.CONNECT;
import static bef.rest.befrest.utils.SDKConst.KEEP_PINGING;
import static bef.rest.befrest.utils.SDKConst.NETWORK_CONNECTED;
import static bef.rest.befrest.utils.SDKConst.NETWORK_DISCONNECTED;
import static bef.rest.befrest.utils.SDKConst.PING;
import static bef.rest.befrest.utils.SDKConst.REFRESH;
import static bef.rest.befrest.utils.SDKConst.RETRY;
import static bef.rest.befrest.utils.SDKConst.RETRY_INTERVAL;
import static bef.rest.befrest.utils.SDKConst.SERVICE_STOPPED;
import static bef.rest.befrest.utils.SDKConst.connectWakeLockName;
import static bef.rest.befrest.utils.SDKConst.prevFailedConnectTries;

public class Util {

    private static String TAG = Util.class.getName();
    public static int netWorkType = 0;

    public static String getIntentEvent(Intent intent) {
        if (intent != null) {
            if (intent.getBooleanExtra(CONNECT, false))
                return CONNECT;
            if (intent.getBooleanExtra(REFRESH, false))
                return REFRESH;
            if (intent.getBooleanExtra(RETRY, false))
                return RETRY;
            if (intent.getBooleanExtra(NETWORK_CONNECTED, false))
                return NETWORK_CONNECTED;
            if (intent.getBooleanExtra(NETWORK_DISCONNECTED, false))
                return NETWORK_DISCONNECTED;
            if (intent.getBooleanExtra(SERVICE_STOPPED, false))
                return SERVICE_STOPPED;
            if (intent.getBooleanExtra(PING, false))
                return PING;
            if (intent.getBooleanExtra(KEEP_PINGING, false))
                return KEEP_PINGING;
        }
        return "NOT_ASSIGNED";
    }

    @SuppressLint("MissingPermission")
    public static boolean isConnectedToInternet(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                return true;
            }
            netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                netWorkType = 1;
                return true;
            } else {
                netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    netWorkType = 2;
                    return true;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    public static String getBroadcastSendingPermission(Context context) {
        return context.getApplicationContext().getPackageName() + BROADCAST_SENDING_PERMISSION_POSTFIX;
    }

    public static String decodeBase64(String s) {
        try {
            return new String(Base64.decode(s, Base64.DEFAULT), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    static String getDeviceInfo() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " - " + model + " - ";
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }
        return phrase.toString();
    }

    public static Comparator<BefrestMessage> comparator =
            (lhs, rhs) -> lhs.getTimeStamp().compareTo(rhs.getTimeStamp());

    public static int getNextReconnectInterval() {
        return RETRY_INTERVAL[prevFailedConnectTries < RETRY_INTERVAL.length ? prevFailedConnectTries : RETRY_INTERVAL.length - 1];
    }

    public static String getRandomNumber() {
        return String.valueOf((int) (Math.random() * 11000));
    }

    @SuppressLint("InvalidWakeLockTag")
    public static PowerManager.WakeLock acquireConnectWakeLock(PowerManager.WakeLock
                                                                       connectWakelock) {
        if (Util.isWakeLockPermissionGranted()) {
            if (connectWakelock != null && connectWakelock.isHeld()) {
                return connectWakelock;
            }
            Context ctx = Befrest.getInstance().getContext();
            if (ctx == null) {
                BefrestLog.i(TAG, "could not acquire connect wakelock");
                return null;
            }
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            connectWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, connectWakeLockName);
            connectWakelock.setReferenceCounted(false);
            connectWakelock.acquire(10 * 60 * 1000L /*10 minutes*/);
            BefrestLog.i(TAG, "acquiredConnectWakelock");
            return connectWakelock;
        }
        BefrestLog.i(TAG, "could not acquire connect wakelock. (permission not granted)");
        return null;
    }

    private static boolean isWakeLockPermissionGranted() {
        if (Befrest.getInstance().isBefrestInitialized())
            return Befrest.getInstance().getContext().checkCallingOrSelfPermission(
                    Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED;
        return false;
    }

    public static String encodeToBase64(String s){
        try {
            byte[] data = s.getBytes("UTF-8");
            return Base64.encodeToString(data, Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public static String stackTraceToString(Exception e){
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        return result.toString();
    }
    /**
     * use for fireBase
     */
    static boolean customReceiverPass(Context context) throws
            PackageManager.NameNotFoundException {
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
            if (filter != null && filter.hasAction("android.net.conn.CONNECTIVITY_CHANGE")) {
                boolean enabled = pm.getComponentEnabledSetting(new ComponentName(
                        activityInfo.packageName, activityInfo.name
                )) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                BefrestLog.v(TAG, "Handle receiver: " + activityInfo.name +
                        "; enabled: " + enabled);
                return enabled;
            }
        }
        // Not found
        return true;
    }
}
