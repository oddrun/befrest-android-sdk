package bef.rest.befrest.befrest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.SystemClock;

import bef.rest.befrest.Befrest;
import bef.rest.befrest.BefrestConnectivityChangeReceiver;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.utils.WatchSdk;

import static bef.rest.befrest.utils.SDKConst.ACTION_BEFREST_PUSH;
import static bef.rest.befrest.utils.SDKConst.BROADCAST_TYPE;
import static bef.rest.befrest.utils.SDKConst.KEY_TIME_SENT;
import static bef.rest.befrest.utils.SDKConst.SERVICE_STOPPED;
import static bef.rest.befrest.utils.SDKConst.START_ALARM_CODE;
import static bef.rest.befrest.utils.SDKConst.START_SERVICE_AFTER_ILLEGAL_STOP_DELAY;


public class BefrestContract {
    private static final String TAG = "BefrestContract";
    private BefrestConnectivityChangeReceiver befrestConnectivityChangeReceiver;

    private BefrestContract() {
        befrestConnectivityChangeReceiver = new BefrestConnectivityChangeReceiver();
    }

    private static class Loader {
        private static volatile BefrestContract instance = new BefrestContract();
    }

    public static BefrestContract getInstance() {
        return Loader.instance;
    }

    public void sendBefrestBroadcast(Context context, int type, Bundle extras) {
        try {
            Intent intent = new Intent(ACTION_BEFREST_PUSH);
            intent.putExtra(BROADCAST_TYPE, type);
            if (extras != null) intent.putExtras(extras);
            String permission = Util.getBroadcastSendingPermission(context);
            long now = System.currentTimeMillis();
            intent.putExtra(KEY_TIME_SENT, "" + now);
            context.getApplicationContext().sendBroadcast(intent, permission);
        } catch (Exception e) {
            WatchSdk.reportCrash(e, "can't BroadCast Messages");
        }
    }

    public void registerBroadcastReceiver() {
        try {
            if (!Befrest.getInstance().isBefrestInitialized()) {
                BefrestLog.e(TAG, "Befrest not initialized call Befrest.init() in Application Class");
                return;
            }
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            Befrest.getInstance().getContext().registerReceiver(befrestConnectivityChangeReceiver, filter);
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
            e.printStackTrace();
        }
    }

    public void unRegisterBroadCastReceiver() {
        try {
            if (!Befrest.getInstance().isBefrestInitialized()) {
                BefrestLog.e(TAG, "Befrest not initialized call Befrest.init() in Application Class");
                return;
            }
            Befrest.getInstance().getContext().unregisterReceiver(befrestConnectivityChangeReceiver);
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
            e.printStackTrace();
        }
    }


    public void setAlarmService() {
        if (Befrest.getInstance().isBefrestInitialized()) {
            BefrestLog.e(TAG, "befrest is not initialized yet");
            return;
        }
        Context ctx = Befrest.getInstance().getContext();
        AlarmManager alarmMgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, Befrest.getInstance().getPushService()).putExtra(SERVICE_STOPPED, true);
        PendingIntent pi = PendingIntent.getService(ctx, START_ALARM_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + START_SERVICE_AFTER_ILLEGAL_STOP_DELAY,
                3 * 60 * 1000, pi);
    }


    public void reportOnClose(int code) {
    }

    public void reportOnOpen(Context context) {
    }
}
