package bef.rest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public abstract class NotificationReceiver extends BroadcastReceiver {

    public static String befrestPush = "bef.rest.broadcasts.ACTION_BEFREST_P";
    private NotificationHandler notificationHandler;

    @Override
    public void onReceive(Context context, Intent intent) {
        notificationHandler = new NotificationHandler(context);
        Bundle args = intent.getBundleExtra("DATA");
        BefrestNotifications befrestNotifications = (BefrestNotifications) args.getSerializable("Notification");
        beforeShowCallBack(befrestNotifications);


    }

    public void beforeShowCallBack(BefrestNotifications befrestNotifications) {
        showNotification(befrestNotifications);

    }

    private void showNotification(BefrestNotifications befrestNotifications) {
        notificationHandler.setmBefrestNotifications(befrestNotifications);
        notificationHandler.showNotification();
    }


}
