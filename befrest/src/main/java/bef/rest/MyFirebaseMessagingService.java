package bef.rest;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static bef.rest.BefrestInternal.Util.customReceiverPass;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static String TAG = BefLog.TAG_PREF + " MyFirebaseMessagingService";
    private BefrestNotifications builder;
    HashMap<String, String> clientData = new HashMap<>();

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.i(TAG, "onMessageReceived: ");
        if (remoteMessage.getData().size() > 0) {
            try {
                if (!customReceiverPass(getApplicationContext())) {
                    handleDataMessage(remoteMessage.getData());
                    builder.setData(clientData);
                    Intent intent = new Intent(NotificationReceiver.befrestPush);
                    Bundle args = new Bundle();
                    args.putSerializable("Notification", builder);
                    intent.putExtra("DATA", args);
                    getApplicationContext().sendBroadcast(intent);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            } catch (JSONException e) {
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
                case "x_group":
                    builder.setGroup(entry.getValue());
                    break;
                default:
                    clientData.put(entry.getKey(), entry.getValue());
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
            JSONObject payload = c.has("actionPayload") ? c.getJSONObject("actionPayload") : null;
            String stringPayload = payload != null ? payload.toString() : "";
            String type = c.getString("actionType");
            actions.add(new BefrestActionNotification(title, stringPayload, type));
        }
        builder.setClickAction(actions);
    }
}
