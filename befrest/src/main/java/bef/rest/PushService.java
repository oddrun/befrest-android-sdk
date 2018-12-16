/**
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
 * <p>
 * If receiving befrest events through broadcast receivers does not meet
 * your needs you can implement your custom push service class that
 * extends this class and introduce it to befrest using
 * {@code BefrestFactory.getInstance(context).setCustomPushService(YourCustomPushService.class)}
 */


/**
 * If receiving befrest events through broadcast receivers does not meet
 * your needs you can implement your custom push service class that
 * extends this class and introduce it to befrest using
 * {@code BefrestFactory.getInstance(context).setCustomPushService(YourCustomPushService.class)}
 */
package bef.rest;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PushService extends Service {
    private static final String TAG = BefLog.TAG_PREF + "PushService";

    //events
    /* package */ static final String NETWORK_CONNECTED = "NETWORK_CONNECTED";
    /* package */ static final String NETWORK_DISCONNECTED = "NETWORK_DISCONNECTED";
    /* package */ static final String CONNECT = "CONNECT";
    /* package */ static final String REFRESH = "REFRESH";
    /* package */ static final String SERVICE_STOPPED = "SERVICE_STOPPED";
    /* package */ static final String RETRY = "RETRY";
    /* package */ static final String PING = "PING";
    /* package */ static final String KEEP_PINGING = "KEEP_PINGING";


    //retrying variables and constants
    private boolean retryInProgress;
    private static final int[] RETRY_INTERVAL = {0, 2 * 1000, 5 * 1000, 10 * 1000, 18 * 1000, 40 * 1000, 100 * 1000, 240 * 1000};
    private static int prevFailedConnectTries;
    private Runnable retry = new Runnable() {
        @Override
        public void run() {
            handleEvent(RETRY);
        }
    };

    //bach mode variables and constants
    public static final int TIME_PER_MESSAGE_IN_BATH_MODE = 30;
    private static final int BATCH_MODE_TIMEOUT = 3000;
    private static int batchSize;
    private boolean isBachReceiveMode;

    static final int START_SERVICE_AFTER_ILLEGAL_STOP_DELAY = 15 * 1000;

    private boolean authProblemSinceLastStart = false;

    private List<BefrestMessage> receivedMessages = new ArrayList<>();
    private BefrestInternal befrestProxy;
    private BefrestImpl befrestActual;

    private Handler handler;
    private Handler mainThreadHandler = new Handler();
    private BefrestConnection mConnection;
    private HandlerThread befrestHandlerThread;

    private WebSocketConnectionHandler wscHandler;

    private BroadcastReceiver screenAndConnectionStateBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                BefLog.i(TAG, "Broadcast Received. action: " + action);
                switch (action) {
                    case Intent.ACTION_SCREEN_ON:
                        internalRefreshIfPossible();
                        break;
                    case Intent.ACTION_SCREEN_OFF:
                        break;
                    case ConnectivityManager.CONNECTIVITY_ACTION:
                        if (BefrestImpl.Util.isConnectedToInternet(context))
                            handleEvent(NETWORK_CONNECTED);
                        else handleEvent(NETWORK_DISCONNECTED);
                }
            } catch (Throwable t) {

                throw t;
            }
        }
    };

    private Runnable finishBatchMode = new Runnable() {
        @Override
        public void run() {
            int receivedMsgs = receivedMessages.size();
            if ((receivedMsgs >= batchSize - 1))
                isBachReceiveMode = false;
            else {
                batchSize -= receivedMsgs;
                handler.postDelayed(finishBatchMode, getBatchTime());
            }
            if (receivedMsgs > 0)
                handleReceivedMessages();
        }
    };

    Runnable connRefreshed = new Runnable() {
        @Override
        public void run() {
            onConnectionRefreshed();
        }
    };

    Runnable befrestConnected = new Runnable() {
        @Override
        public void run() {
            onBefrestConnected();
        }
    };

    Runnable authProblem = new Runnable() {
        @Override
        public void run() {
            onAuthorizeProblem();
        }
    };

    Comparator<BefrestMessage> comparator = new Comparator<BefrestMessage>() {
        @Override
        public int compare(BefrestMessage lhs, BefrestMessage rhs) {
            return lhs.timeStamp.compareTo(rhs.timeStamp);
        }
    };

    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public final boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public final void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public final void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public final void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public final void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    @Override
    public final void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public final void onCreate() {
        BefLog.i(TAG, "PushService: " + System.identityHashCode(this) + "  onCreate()");
        prevFailedConnectTries = 0;
        befrestProxy = BefrestFactory.getInternalInstance(this);
        befrestActual = ((BefrestInvocHandler) Proxy.getInvocationHandler(befrestProxy)).obj;
        createWebsocketConnectionHanlder();
        befrestHandlerThread = new HandlerThread("BefrestThread");
        befrestHandlerThread.start();
        mConnection = new BefrestConnection(this, befrestHandlerThread.getLooper(), wscHandler, befrestProxy.getSubscribeUri(), befrestProxy.getSubscribeHeaders());
        registerBroadCastReceivers();
        handler = new Handler(befrestHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    super.handleMessage(msg);
                } catch (Throwable t) {

                    throw t;
                }
            }
        };
        super.onCreate();
    }

    private void createWebsocketConnectionHanlder() {
        wscHandler = new WebSocketConnectionHandler() {

            @Override
            public void onOpen() {
                BefLog.i(TAG, "Befrest Connected");
                befrestProxy.reportOnOpen(PushService.this);
                prevFailedConnectTries = 0;
                befrestActual.prevAuthProblems = 0;
                mainThreadHandler.post(befrestConnected);
                cancelFutureRetry();
            }

            @Override
            public void onBefrestMessage(BefrestMessage msg) {
                switch (msg.type) {
                    case NORMAL:
                    case TOPIC:
                    case GROUP:
                        BefLog.i(TAG, "Befrest Push Received:: " + msg);
                        receivedMessages.add(msg);
                        if (!isBachReceiveMode)
                            handleReceivedMessages();
                        break;
                    case BATCH:
                        BefLog.i(TAG, "Befrest Push Received:: " + msg.type + "  " + msg);
                        isBachReceiveMode = true;
                        batchSize = Integer.valueOf(msg.data);
                        int batchTime = getBatchTime();
                        BefLog.i(TAG, "BATCH Mode Started for : " + batchTime + "ms");
                        handler.postDelayed(finishBatchMode, batchTime);
                        break;
                }
            }

            @Override
            public void onConnectionRefreshed() {
                notifyConnectionRefreshedIfNeeded();
            }

            @Override
            public void onClose(int code, String reason) {
                BefLog.i(TAG, "WebsocketConnectionHandler: " + System.identityHashCode(this) + "Connection lost. Code: " + code + ", Reason: " + reason);
                BefLog.i(TAG, "Befrest Connection Closed. Will Try To Reconnect If Possible.");
                befrestProxy.reportOnClose(PushService.this, code);
                switch (code) {
                    case CLOSE_UNAUTHORIZED:
                        handleAthorizeProblem();
                        break;
                    case CLOSE_CANNOT_CONNECT:
                    case CLOSE_CONNECTION_LOST:
                    case CLOSE_INTERNAL_ERROR:
                    case CLOSE_NORMAL:
                    case CLOSE_CONNECTION_NOT_RESPONDING:
                    case CLOSE_PROTOCOL_ERROR:
                    case CLOSE_SERVER_ERROR:
                    case CLOSE_HANDSHAKE_TIME_OUT:
                        scheduleReconnect();
                }
            }
        };
    }

    @Override
    public final int onStartCommand(Intent intent, int flags, int startId) {
        handleEvent(getIntentEvent(intent));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        BefLog.i(TAG, "PushService: " + System.identityHashCode(this) + "==================onDestroy()_START===============");
        cancelFutureRetry();
        mConnection.forward(new BefrestEvent(BefrestEvent.Type.DISCONNECT));
        mConnection.forward(new BefrestEvent(BefrestEvent.Type.STOP));
        try {
            befrestHandlerThread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        unRegisterBroadCastReceiver();
        if (befrestActual.isBefrestStarted)
            befrestProxy.setStartServiceAlarm();
        mConnection = null;
        befrestHandlerThread = null;
        super.onDestroy();
        BefLog.i(TAG, "PushService==================onDestroy()_END===============");
    }

    @Override
    public final void onTaskRemoved(Intent rootIntent) {
        BefLog.i(TAG, "PushService onTaskRemoved: ");
        if (befrestActual.isBefrestStarted)
            befrestProxy.setStartServiceAlarm();
        super.onTaskRemoved(rootIntent);
    }

    private void handleEvent(String command) {
        BefLog.i(TAG, "PushService:" + System.identityHashCode(this) + " handleEvent( " + command + " )");
        switch (command) {
            case NETWORK_CONNECTED:
            case CONNECT:
                connectIfNetworkAvailable();
                break;
            case REFRESH:
                refresh();
                break;
            case RETRY:
                retryInProgress = false;
                connectIfNetworkAvailable();
                break;
            case NETWORK_DISCONNECTED:
                cancelFutureRetry();
                mConnection.forward(new BefrestEvent(BefrestEvent.Type.DISCONNECT));
                break;
            case SERVICE_STOPPED:
                handleServiceStopped();
                break;
            case PING:
                mConnection.forward(new BefrestEvent(BefrestEvent.Type.PING));
                break;
            case KEEP_PINGING:
                internalRefreshIfPossible();
                break;
            default:
                connectIfNetworkAvailable();
        }
    }

    private void connectIfNetworkAvailable() {
        if (retryInProgress) {
            cancelFutureRetry();
            prevFailedConnectTries = 0;
        }
        if (BefrestImpl.Util.isConnectedToInternet(this))
            mConnection.forward(new BefrestEvent(BefrestEvent.Type.CONNECT));
    }

    private String getIntentEvent(Intent intent) {
        if (intent != null) {
            if (intent.getBooleanExtra(CONNECT, false))
                return CONNECT;
            if (intent.getBooleanExtra(REFRESH, false))
                return REFRESH;
            if (intent.getBooleanExtra(NETWORK_CONNECTED, false))
                return NETWORK_CONNECTED;
            if (intent.getBooleanExtra(NETWORK_DISCONNECTED, false))
                return NETWORK_DISCONNECTED;
            if (intent.getBooleanExtra(SERVICE_STOPPED, false))
                return SERVICE_STOPPED;
            if (intent.getBooleanExtra(PING, false))
                return PING;
            if (intent.getBooleanExtra(KEEP_PINGING, false))
                return KEEP_PINGING;
        }
        return "NOT_ASSIGNED";
    }

    private void registerBroadCastReceivers() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(screenAndConnectionStateBroadCastReceiver, filter);
    }

    private void unRegisterBroadCastReceiver() {
        unregisterReceiver(screenAndConnectionStateBroadCastReceiver);
    }

    private void scheduleReconnect() {
        boolean hasNetworkConnection = BefrestImpl.Util.isConnectedToInternet(this);
        BefLog.v(TAG, "scheduleReconnect() retryInProgress, hasNetworkConnection", retryInProgress, hasNetworkConnection);
        if (retryInProgress || !hasNetworkConnection)
            return; //a retry or restart is already in progress or network in unavailable
        cancelFutureRetry(); //just to be sure
        prevFailedConnectTries++;
        int interval = getNextReconnectInterval();
        BefLog.i(TAG, "Befrest Will Retry To Connect In " + interval + "ms");
        handler.postDelayed(retry, getNextReconnectInterval());
        retryInProgress = true;
    }

    private void handleServiceStopped() {
        if (befrestActual.isBefrestStarted) {
            if (!(retryInProgress))
                connectIfNetworkAvailable();
        } else {
            //stopSelf();
        }
    }

    private void handleAthorizeProblem() {
        if (befrestActual.prevAuthProblems == 0)
            mainThreadHandler.post(authProblem);
        else if (authProblemSinceLastStart)
            mainThreadHandler.post(authProblem);
        cancelFutureRetry();
        handler.postDelayed(retry, befrestProxy.getSendOnAuthorizeBroadcastDelay());
        retryInProgress = true;
        befrestActual.prevAuthProblems++;
        authProblemSinceLastStart = true;
    }

    private void notifyConnectionRefreshedIfNeeded() {
        if (befrestActual.refreshIsRequested) {
            mainThreadHandler.post(connRefreshed);
            befrestActual.refreshIsRequested = false;
            BefLog.i(TAG, "Befrest Refreshed");
        }
    }

    private void cancelFutureRetry() {
        BefLog.i(TAG, "cancelFutureRetry()");
        handler.removeCallbacks(retry);
        retryInProgress = false;
    }

    private int getNextReconnectInterval() {
        return RETRY_INTERVAL[prevFailedConnectTries < RETRY_INTERVAL.length ? prevFailedConnectTries : RETRY_INTERVAL.length - 1];
    }

    private int getBatchTime() {
        int requiredTime = TIME_PER_MESSAGE_IN_BATH_MODE * batchSize;
        return (requiredTime < BATCH_MODE_TIMEOUT ? requiredTime : BATCH_MODE_TIMEOUT);
    }

    private void refresh() {
        if (retryInProgress) {
            cancelFutureRetry();
            prevFailedConnectTries = 0;
        }
        mConnection.forward(new BefrestEvent(BefrestEvent.Type.REFRESH));
    }

    private void internalRefreshIfPossible() {
        BefLog.i(TAG, "internalRefreshIfPossible");
        if (BefrestImpl.Util.isConnectedToInternet(this) && befrestActual.isBefrestStarted) {
            refresh();
        }
    }

    private void handleReceivedMessages() {
        BefLog.w(TAG, "handleReceivedMessages: ");
        final ArrayList<BefrestMessage> msgs = new ArrayList<>(receivedMessages.size());
        msgs.addAll(receivedMessages);
        receivedMessages.clear();
        Collections.sort(msgs, comparator);
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                onPushReceived(msgs);
            }
        });
    }

    /**
     * Called when new push messages are received.
     * The method is called in main thread of the application (UiThread)
     *
     * call super() if you want to receive this callback also in your broadcast receivers.
     *
     * @param messages messages
     */
    protected void onPushReceived(ArrayList<BefrestMessage> messages) {
        BefLog.w(TAG,"onPushReceived");
        Parcelable[] data = new BefrestMessage[messages.size()];
        Bundle b = new Bundle(1);
        b.putParcelableArray(BefrestImpl.Util.KEY_MESSAGE_PASSED, messages.toArray(data));

        befrestProxy.sendBefrestBroadcast(this, BefrestPushReceiver.PUSH, b);
    }

    /**
     * Called when there is a problem with your Authentication token. The Service encounters authorization errors while trying to connect to Befrest servers.
     * The method is called in main thread of the application (UiThread)
     *
     * call super() if you want to receive this callback also in your broadcast receivers.
     *
     */
    protected void onAuthorizeProblem() {
        befrestProxy.sendBefrestBroadcast(this, BefrestPushReceiver.UNAUTHORIZED, null);
    }

    /**
     * Connection Refreshed! This method is called when Befrest refreshes its connection to server in respond to a refresh request from user
     * The method is called in main thread of the application (UiThread)
     *
     * call super() if you want to receive this callback also in your broadcast receivers.
     */
    protected void onConnectionRefreshed() {
        befrestProxy.sendBefrestBroadcast(this, BefrestPushReceiver.CONNECTION_REFRESHED, null);
    }

    /**
     * is called when Befrest Connects to its server.
     * The method is called in main thread of the application (UiThread)
     *
     * call super() if you want to receive this callback also in your broadcast receivers.
     */
    protected void onBefrestConnected() {
        befrestProxy.sendBefrestBroadcast(this, BefrestPushReceiver.BEFREST_CONNECTED, null);
    }
}