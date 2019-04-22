/*
 Copyright 2015-2016 Befrest
 <p/>
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 <p/>
 http://www.apache.org/licenses/LICENSE-2.0
 <p/>
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package bef.rest.befrest.befrest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.Util;

import static bef.rest.befrest.utils.SDKConst.NETWORK_CONNECTED;
import static bef.rest.befrest.utils.SDKConst.NETWORK_DISCONNECTED;
import static bef.rest.befrest.utils.SDKConst.REFRESH;

public final class BefrestConnectivityChangeReceiver extends BroadcastReceiver {
    private static final String TAG = BefrestConnectivityChangeReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String flag;
        if (action != null) {
            switch (action) {
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (Util.isConnectedToInternet(context)) {
                        BefrestLog.w(TAG, "Connection Changed " + NETWORK_CONNECTED);
                        flag = NETWORK_CONNECTED;
                    } else {
                        BefrestLog.w(TAG, "Connection Changed " + NETWORK_DISCONNECTED);
                        flag = NETWORK_DISCONNECTED;
                    }
                    Befrest.getInstance().startService(flag);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    BefrestLog.d(TAG, "Screen off");
                    break;
                case Intent.ACTION_SCREEN_ON:
                    BefrestLog.d(TAG, "Screen on");
                    Befrest.getInstance().startService(REFRESH);
                    break;
            }
        }
    }
}