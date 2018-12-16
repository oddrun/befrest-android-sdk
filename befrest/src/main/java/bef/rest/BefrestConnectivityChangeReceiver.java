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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import java.lang.reflect.Proxy;

/**
 * Created by ehsan on 11/24/2015.
 */
public final class BefrestConnectivityChangeReceiver extends BroadcastReceiver {
    private static final String TAG = BefLog.TAG_PREF + "BefrestConnectivityChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            String flag = "";
            BefLog.v(TAG, "Broadcast received: action=" + action);
            Class<?> pushService = ((BefrestInvocHandler) Proxy.getInvocationHandler(BefrestFactory.getInternalInstance(context))).obj.pushService;
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
                if (BefrestImpl.Util.isConnectedToInternet(context))
                    flag = PushService.NETWORK_CONNECTED;
                else
                    flag = PushService.NETWORK_DISCONNECTED;
            BefrestImpl.startService(pushService, context, flag);
        } catch (Throwable t) {

            throw t;
        }
    }
}