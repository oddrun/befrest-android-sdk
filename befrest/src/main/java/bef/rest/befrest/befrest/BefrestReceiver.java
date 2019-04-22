package bef.rest.befrest.befrest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;

import bef.rest.befrest.utils.SDKConst;

import static bef.rest.befrest.utils.SDKConst.BEFREST_CONNECTED;
import static bef.rest.befrest.utils.SDKConst.BEFREST_CONNECTION_CHANGED;
import static bef.rest.befrest.utils.SDKConst.BROADCAST_TYPE;
import static bef.rest.befrest.utils.SDKConst.CONNECTION_REFRESHED;
import static bef.rest.befrest.utils.SDKConst.PUSH;
import static bef.rest.befrest.utils.SDKConst.UNAUTHORIZED;

public class BefrestReceiver extends BroadcastReceiver {
    int broadcastType = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        broadcastType = intent.getIntExtra(BROADCAST_TYPE, -1);
        switch (broadcastType) {
            case PUSH:
                onPushReceived(context, prepareMessage(intent));
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
            case BEFREST_CONNECTION_CHANGED:
                onConnectionChanged(isConnect(intent));
                break;
        }

    }

    public void onConnectionChanged(boolean isConnect) {

    }

    public void onBefrestConnected(Context context) {
    }

    public void onPushReceived(Context context, BefrestMessage[] bm) {
    }


    public void onAuthorizeProblem(Context context) {
    }

    public void onConnectionRefreshed(Context context) {
    }

    private BefrestMessage[] prepareMessage(Intent intent) {
        Parcelable[] p = intent.getParcelableArrayExtra(SDKConst.KEY_MESSAGE_PASSED);
        BefrestMessage[] bm = new BefrestMessage[p.length];
        System.arraycopy(p, 0, bm, 0, p.length);
        return bm;
    }

    private boolean isConnect(Intent intent) {
        return intent.getBooleanExtra(SDKConst.KEY_MESSAGE_PASSED, true);
    }
}
