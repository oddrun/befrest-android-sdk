package bef.rest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

class NotificationHandler {
    private String NOTIFICATION_CHANNEL_ID = "my_channel_id_01";
    private Context mContext;
    private BefrestNotifications mBefrestNotifications;
    private NotificationCompat.Builder notification;
    private int icon;
    private NotificationManager notifManager;
    private String GROUP_KEY_WORK_EMAIL = "bef.rest.GROUP";

    NotificationHandler(Context mContext, BefrestNotifications mBefrestNotifications) throws PackageManager.NameNotFoundException {
        this.mContext = mContext;
        this.mBefrestNotifications = mBefrestNotifications;
        ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), 0);
        icon = applicationInfo.icon;


    }

    void showNotification() throws JSONException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannels();
        }
        notification = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID);
        notification.setStyle(getInboxStyle());

        notification.setContentTitle(mBefrestNotifications.getTitle())
                .setContentText(mBefrestNotifications.getBody())
                .setSmallIcon(mBefrestNotifications.getSmallIcon() != null ? getResId(mBefrestNotifications.getSmallIcon()) : icon);

        Intent intent = getRelatedPendingIntent(new BefrestActionNotification("", mBefrestNotifications.getClick_payload() != null ? new JSONObject(mBefrestNotifications.getClick_payload()) : null, mBefrestNotifications.getClick() != null ? mBefrestNotifications.getClick() : ""));
        intent = addExtraDataToIntent(intent);
        notification.setContentIntent(getpendingIntent(intent));
        if (mBefrestNotifications.getGroup() != null) {
            notification.setGroup(mBefrestNotifications.getGroup() != null ? mBefrestNotifications.getGroup() : GROUP_KEY_WORK_EMAIL);
        }
        notification.setAutoCancel(true);

        if (mBefrestNotifications.getIcon() != null) {
            Bitmap b = getBitmapFromURL(mBefrestNotifications.getIcon());
            if (b != null)
                notification.setLargeIcon(b);
        }

        if (mBefrestNotifications.getSound() != null)
            setSound(mBefrestNotifications.getSound());
        if (mBefrestNotifications.getClickAction() != null && mBefrestNotifications.getClickAction().size() > 1)
            handleClickAction(mBefrestNotifications.getClickAction());
        Notification notifications = notification.build();
        notifications.flags |= Notification.FLAG_AUTO_CANCEL;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        notificationManager.notify(getRandomNumber(), notification.build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            notificationManager.notify(0, buildSummeryNotification());
    }

    private Intent addExtraDataToIntent(Intent intent) {
        if (mBefrestNotifications.getData() != null && mBefrestNotifications.getData().size() > 0) {
            for (Map.Entry<String, String> entry : mBefrestNotifications.getData().entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }
        return intent;
    }

    private int getResId(String resName) {
        try {
            Field idField = R.drawable.class.getDeclaredField(resName);
            return idField.getInt(idField);
        } catch (Exception e) {
            e.printStackTrace();
            return icon;
        }
    }

    private int getRandomNumber() {
        return new Random().nextInt(61) + 20;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    private Notification buildSummeryNotification() throws JSONException {
        Intent intent = getRelatedPendingIntent(new BefrestActionNotification("", mBefrestNotifications.getClick_payload() != null ? new JSONObject(mBefrestNotifications.getClick_payload()) : null, mBefrestNotifications.getClick() != null ? mBefrestNotifications.getClick() : ""));
        if (mBefrestNotifications.getData() != null && mBefrestNotifications.getData().size() > 0) {
            for (Map.Entry<String, String> entry : mBefrestNotifications.getData().entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(mBefrestNotifications.getTitle())
                .setContentText(mBefrestNotifications.getBody())
                .setSmallIcon(mBefrestNotifications.getSmallIcon() != null ? getResId(mBefrestNotifications.getSmallIcon()) : icon)
                .setStyle(getInboxStyle())
                .setContentIntent(getpendingIntent(intent));
        if (mBefrestNotifications.getGroup() != null) {
            b.setGroup(mBefrestNotifications.getGroup() != null ? mBefrestNotifications.getGroup() : GROUP_KEY_WORK_EMAIL).setGroupSummary(true);
        }
        Notification notifications = b.build();
        notifications.flags |= Notification.FLAG_AUTO_CANCEL;
        return notifications;
    }

    private NotificationCompat.InboxStyle getInboxStyle() {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setSummaryText(mBefrestNotifications.getTitle())
                .setBigContentTitle(mBefrestNotifications.getBody())
                .addLine("");
        return inboxStyle;
    }

    private void handleClickAction(ArrayList<BefrestActionNotification> clickAction) throws JSONException {
        for (int i = 0; i < clickAction.size(); i++) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                notification.addAction(1, clickAction.get(i).getActionTitle(), getpendingIntent(getRelatedPendingIntent(clickAction.get(i))));
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    private Intent getRelatedPendingIntent(BefrestActionNotification befrestActionNotification) throws JSONException {

        switch (befrestActionNotification.getActionType()) {
            case "openActivity":
                String activityToStart = mContext.getPackageName() + "." + befrestActionNotification.getJsonObject().getString("content");
                try {
                    Class<?> c = Class.forName(activityToStart);
                    Intent intent = new Intent(mContext, c);
                    return addExtraDataToIntent(intent);
                } catch (ClassNotFoundException ignored) {
                }
                break;

            case "openApp":
                return mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());

            case "dialer":
                Uri u = Uri.parse("tel:" + befrestActionNotification.getJsonObject().getString("to"));
                return new Intent(Intent.ACTION_DIAL, u);

            case "sms":
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.putExtra("sms_body", befrestActionNotification.getJsonObject().getString("content"));
                intent.putExtra("address", befrestActionNotification.getJsonObject().getString("to"));
                intent.setType("vnd.android-dir/mms-sms");
                return intent;
            case "email":
                String mailto = "mailto:" + befrestActionNotification.getJsonObject().getString("to") +
                        "?cc=" + "" +
                        "&subject=" + Uri.encode(befrestActionNotification.getJsonObject().getString("title")) +
                        "&body=" + Uri.encode(befrestActionNotification.getJsonObject().getString("content"));
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse(mailto));
                return emailIntent;

            default:
                break;

        }
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
        return addExtraDataToIntent(intent);
    }


    private PendingIntent getpendingIntent(Intent intent) {
        return PendingIntent.getActivity(
                mContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void setSound(String sound) {
        try {
            Uri alarmSound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + mContext.getPackageName() + "/raw/" + sound);
            Ringtone r = RingtoneManager.getRingtone(mContext, alarmSound);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Bitmap getBitmapFromURL(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createChannels() {
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "BefrestChannel", NotificationManager.IMPORTANCE_LOW
        );
        notificationChannel.setDescription("");
        notificationChannel.setSound(null, null);
        notificationChannel.enableLights(false);
        notificationChannel.setLightColor(Color.BLUE);
        notificationChannel.enableVibration(false);
        getManager().createNotificationChannel(notificationChannel);
    }

    private NotificationManager getManager() {
        if (notifManager == null)
            notifManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        return notifManager;
    }
}
