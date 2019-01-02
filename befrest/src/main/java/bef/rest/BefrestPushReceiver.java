/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package bef.rest;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;

/**
 * Override this class to make register receivers. You can register receivers
 * either dynamically in code or statically in projects AndroidManifest file.
 */
public abstract class BefrestPushReceiver extends BroadcastReceiver {
    private static final String TAG = BefLog.TAG_PREF + "BefrestPushReceiver";

    static final int PUSH = 0;
    static final int UNAUTHORIZED = 1;
    static final int CONNECTION_REFRESHED = 2;
    static final int BEFREST_CONNECTED = 3;
    static final int CANCEL_NOTIFICATION = 101;
    static final String BROADCAST_TYPE = "BROADCAST_TYPE";
    static final String ACTION_BEFREST_PUSH = "bef.rest.broadcasts.ACTION_BEFREST_PUSH";
    static final String KEY_TIME_SENT = "KEY_TIME_SENT";

    @Override
    public final void onReceive(Context context, Intent intent) {
        int type = intent.getIntExtra(BROADCAST_TYPE, -1);
        BefLog.i(TAG, "onReceive: " + type);
        String timeSent = intent.getStringExtra(KEY_TIME_SENT);
        BefLog.w(TAG, "Broadcast Received :: type: " + type + "      timeSent:" + timeSent);
        switch (type) {
            case PUSH:
                Parcelable[] p = intent.getParcelableArrayExtra(BefrestImpl.Util.KEY_MESSAGE_PASSED);
                BefrestMessage[] bm = new BefrestMessage[p.length];
                System.arraycopy(p, 0, bm, 0, p.length);
                onPushReceived(context, bm);
                break;
            case UNAUTHORIZED:
                onAuthorizeProblem(context);
                break;
            case CONNECTION_REFRESHED:
                onConnectionRefreshed(context);
                break;
            case BEFREST_CONNECTED:
                onBefrestConnected(context);
                break;
            case CANCEL_NOTIFICATION:
                cancelNotification(context);
                break;
            default:
                BefLog.e(TAG, "BefrestImpl Internal ERROR! Unknown BefrestImpl Action!!");
        }
    }

    private void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(CANCEL_NOTIFICATION);
    }

    /**
     * Called when new push messages are received.
     *
     * @param context  Context
     * @param messages messages
     */
    abstract public void onPushReceived(Context context, BefrestMessage[] messages);

    /**
     * Called when there is a problem with your Authentication token. The Service encounters authorization errors while trying to connect ro BefrestImpl servers.
     * The service will stop if an authorization problem happen.
     * <p>
     * Override this method to initialize the service again with right Auth token
     *
     * @param context Context
     */
    public void onAuthorizeProblem(Context context) {
    }

    /**
     * Connection Refreshed! This method is called when BefrestImpl refreshes its connection to server after a refresh request from user
     *
     * @param context Context
     */
    public void onConnectionRefreshed(Context context) {
    }

    /**
     * BefrestImpl Connected! This method is called when BefrestImpl Connects to its server.
     *
     * @param context
     */
    public void onBefrestConnected(Context context) {
    }

    public void onAnomaly(Context context, String data) {
    }
}

