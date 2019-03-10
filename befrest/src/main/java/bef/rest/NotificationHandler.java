package bef.rest;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
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
import java.util.HashMap;
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


    NotificationHandler(Context mContext) {
        this.mContext = mContext;
    }

    void setmBefrestNotifications(BefrestNotifications mBefrestNotifications) {
        this.mBefrestNotifications = mBefrestNotifications;
    }

    void showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannels();
        }
        notification = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID);
        notification.setStyle(getInboxStyle());
        new HttpRequestTask().execute(mBefrestNotifications.getIcon());
    }

    private Intent addExtraDataToIntent(Intent intent) {
        HashMap<String, String> data = mBefrestNotifications.getData();
        if (data != null && data.size() > 0) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
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
        String click_payload = mBefrestNotifications.getClick_payload();
        String click = mBefrestNotifications.getClick();
        HashMap<String, String> data = mBefrestNotifications.getData();
        String smallIcon = mBefrestNotifications.getSmallIcon();
        String group = mBefrestNotifications.getGroup();

        Intent intent = getRelatedPendingIntent(
                new BefrestActionNotification("",
                        click_payload != null ? new JSONObject(click_payload).toString() : null,
                        click != null ? click : "")
        );

        if (data != null && data.size() > 0) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(mBefrestNotifications.getTitle())
                .setContentText(mBefrestNotifications.getBody())
                .setSmallIcon(smallIcon != null ? getResId(smallIcon) : icon)
                .setStyle(getInboxStyle())
                .setContentIntent(getPendingIntent(intent));

        if (group != null) {
            b.setGroup(group).setGroupSummary(true);
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
                notification.addAction(1, clickAction.get(i).getActionTitle(),
                        getPendingIntent(getRelatedPendingIntent(clickAction.get(i))));
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    private Intent getRelatedPendingIntent(BefrestActionNotification bn) throws JSONException {

        JSONObject jsonObject = new JSONObject(bn.getActionPayload());

        switch (bn.getActionType()) {
            case "openActivity":
                String activityToStart = mContext.getPackageName() + "." + jsonObject.getString("content");
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
                Uri u = Uri.parse("tel:" + jsonObject.getString("to"));
                return new Intent(Intent.ACTION_DIAL, u);

            case "sms":
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.putExtra("sms_body", jsonObject.getString("content"));
                intent.putExtra("address", jsonObject.getString("to"));
                intent.setType("vnd.android-dir/mms-sms");
                return intent;
            case "email":
                String mailto = "mailto:" + jsonObject.getString("to") +
                        "?cc=" + "" +
                        "&subject=" + Uri.encode(jsonObject.getString("title")) +
                        "&body=" + Uri.encode(jsonObject.getString("content"));
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse(mailto));
                return emailIntent;

            default:
                break;

        }
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
        return addExtraDataToIntent(intent);
    }


    private PendingIntent getPendingIntent(Intent intent) {
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

    @SuppressLint("StaticFieldLeak")
    private class HttpRequestTask extends AsyncTask<String, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... strings) {
            try {
                URL url = new URL(strings[0]);
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

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            try {
                show();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void show() throws JSONException {
        String smallIcon = mBefrestNotifications.getSmallIcon();
        String click_payload = mBefrestNotifications.getClick_payload();
        String click = mBefrestNotifications.getClick();
        String group = mBefrestNotifications.getGroup();
        String sound = mBefrestNotifications.getSound();
        ArrayList<BefrestActionNotification> clickAction = mBefrestNotifications.getClickAction();

        notification.setContentTitle(mBefrestNotifications.getTitle())
                .setContentText(mBefrestNotifications.getBody())
                .setSmallIcon(smallIcon != null ? getResId(smallIcon) : icon);

        Intent intent = getRelatedPendingIntent(
                new BefrestActionNotification("",
                        click_payload != null ? new JSONObject(click_payload).toString() : null,
                        click != null ? click : "")
        );
        addExtraDataToIntent(intent);
        notification.setContentIntent(getPendingIntent(intent));

        if (group != null) {
            notification.setGroup(group);
        }
        notification.setAutoCancel(true);

        if (sound != null)
            setSound(sound);

        if (clickAction != null && clickAction.size() > 1)
            handleClickAction(clickAction);
        Notification notifications = notification.build();
        notifications.flags |= Notification.FLAG_AUTO_CANCEL;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        notificationManager.notify(getRandomNumber(), notification.build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            notificationManager.notify(0, buildSummeryNotification());
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
