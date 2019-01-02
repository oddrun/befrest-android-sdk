package bef.rest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.RequiresApi;

public class NotificationUtil {

    private static String TAG = BefLog.TAG_PREF + NotificationUtil.class.getSimpleName();
    private Context mContext;
    private static final String CHANNEL_ONE_ID = "com.bef.rest.notificationchannel";
    private static final String CHANNEL_ONE_NAME = "Channel One";
    private NotificationManager notifManager;

    NotificationUtil(Context mContext) {
        this.mContext = mContext;
    }

    public void handleNotification(BefrestNotifications befrestNotifications) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            handleNotificationAboveOreo(befrestNotifications);
        else
            handleNotificationLowerThanOreo();
    }

    private void handleNotificationLowerThanOreo() {
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleNotificationAboveOreo(BefrestNotifications befrestNotifications) {
        createChannels();

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createChannels() {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                CHANNEL_ONE_NAME, notifManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.RED);
        notificationChannel.setShowBadge(true);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getManager().createNotificationChannel(notificationChannel);
    }

    private NotificationManager getManager() {
        if (notifManager == null) {
            notifManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return notifManager;
    }
}
