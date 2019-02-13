package bef.rest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;

public abstract class notifreceicer extends BroadcastReceiver {

    public static String x = "bef.rest.broadcasts.ACTION_BEFREST_P";
    private NotificationHandler notificationHandler;

    @Override
    public void onReceive(Context context, Intent intent) {

        notificationHandler = new NotificationHandler(context);
        Bundle args = intent.getBundleExtra("DATA");
        BefrestNotifications befrestNotifications = (BefrestNotifications) args.getSerializable("chatobj");
        b(befrestNotifications);


    }

    public void b(BefrestNotifications befrestNotifications) {
        showNotif(befrestNotifications);

    }

    private void showNotif(BefrestNotifications befrestNotifications) {
        notificationHandler.setmBefrestNotifications(befrestNotifications);
        try {
            notificationHandler.showNotification();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


}
