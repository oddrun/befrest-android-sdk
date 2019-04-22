package bef.rest.befrest.befrest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.SystemClock;

import bef.rest.befrest.utils.Util;

import static bef.rest.befrest.utils.SDKConst.ACTION_BEFREST_PUSH;
import static bef.rest.befrest.utils.SDKConst.BROADCAST_TYPE;
import static bef.rest.befrest.utils.SDKConst.KEY_TIME_SENT;
import static bef.rest.befrest.utils.SDKConst.SERVICE_STOPPED;
import static bef.rest.befrest.utils.SDKConst.START_ALARM_CODE;
import static bef.rest.befrest.utils.SDKConst.START_SERVICE_AFTER_ILLEGAL_STOP_DELAY;


public class BefrestContract {

    private BefrestConnectivityChangeReceiver befrestConnectivityChangeReceiver;

    public BefrestContract() {
        befrestConnectivityChangeReceiver = new BefrestConnectivityChangeReceiver();
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

        } catch (Exception ignored) {
        }
    }


    void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        Befrest.getInstance().getContext().registerReceiver(befrestConnectivityChangeReceiver, filter);
    }


    void unRegisterBroadCastReceiver() {
        Befrest.getInstance().getContext().unregisterReceiver(befrestConnectivityChangeReceiver);
    }


    public void setAlarmService(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, Befrest.getInstance().getPushService()).putExtra(SERVICE_STOPPED, true);
        PendingIntent pi = PendingIntent.getService(context, START_ALARM_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + START_SERVICE_AFTER_ILLEGAL_STOP_DELAY,
                3 * 60 * 1000, pi);
    }


    public void reportOnClose(int code) {
        //todo save close connection in sharedPreferences
    }

    public void reportOnOpen(Context context) {
        //todo open connection in sharedPreferences
    }
}
