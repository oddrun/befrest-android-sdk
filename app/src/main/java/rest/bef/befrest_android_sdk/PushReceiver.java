package rest.bef.befrest_android_sdk;

import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import bef.rest.BefrestMessage;
import bef.rest.BefrestPushReceiver;

public class PushReceiver extends BefrestPushReceiver {
    @Override
    public void onPushReceived(Context context, BefrestMessage[] messages) {
        Log.i("BEFRESTAPP", "onPushReceived: *********");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher) //replace with your app icon if it is not correct
                .setTicker("پیام از بفرست!")
                .setContentText(messages[0].getData())
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }
}