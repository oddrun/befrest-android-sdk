package bef.rest;


import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static String TAG = BefLog.TAG_PREF + " MyFirebaseMessagingService";
    private BefrestNotifications builder;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.i(TAG, "onMessageReceived: ");
        if (remoteMessage.getData().size() > 0) {
            Log.i(TAG, "onMessageReceived: " + remoteMessage.getData().toString());
            try {
                handleDataMessage(remoteMessage.getData());
                NotificationHandler handler = new NotificationHandler(getApplicationContext(), builder);
                handler.showNotificationAboveOreo();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleDataMessage(Map<String, String> data) throws JSONException {
        builder = new BefrestNotifications();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            switch (entry.getKey()) {
                case "x_title":
                    builder.setTitle(entry.getValue());
                    break;
                case "x_body":
                    builder.setBody(entry.getValue());
                    break;
                case "x_icon":
                    builder.setIcon(entry.getValue());
                    break;
                case "x_sound":
                    builder.setSound(entry.getValue());
                    break;
                case "x_color":
                    builder.setColor(entry.getValue());
                    break;
                case "x_clickAction":
                    excludeClickAction(entry.getValue());
                    break;
                case "x_click":
                    builder.setClick(entry.getValue());
                    break;
                case "x_clickPayload":
                    builder.setClick_payload(entry.getValue());
                    break;
                case "x_smallIcon":
                    builder.setSmallIcon(entry.getValue());
                    break;
                default:
                    break;
            }
        }
    }

    private void excludeClickAction(String value) throws JSONException {
        JSONObject jsonObject = new JSONObject(value);
        JSONArray data = jsonObject.getJSONArray("data");
        ArrayList<BefrestActionNotification> actions = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject c = data.getJSONObject(i);
            String title = c.getString("actionTitle");
            JSONObject payload = c.has("actionpayload") ? c.getJSONObject("actionpayload") : null;
            //String payload = c.has("actionpayload") ? c.getString("actionpayload") : "0";
            String type = c.getString("actionType");
            actions.add(new BefrestActionNotification(title, payload, type));
        }
        builder.setClickAction(actions);
    }


}
