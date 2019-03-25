/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package bef.rest;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static bef.rest.BefrestPrefrences.PREF_SERVICE_CHOOSER;
import static bef.rest.BefrestPrefrences.getPrefs;

class BefrestConnection extends Handler {
    private static final String TAG = BefLog.TAG_PREF + "BefrestConnection";

    private PowerManager.WakeLock connectWakelock;
    private static final String connectWakeLockName = "befrstconnectwakelock";

    private Looper mLooper;
    private Context appContext;

    private Class<?> pushService;

    private WebSocketReader mReader;
    private WebSocketWriter mWriter;
    private HandlerThread mWriterThread;
    private Socket mTransportChannel;
    private String mWsScheme;
    private String mWsHost;
    private int mWsPort;
    private String mWsPath;
    private String mWsQuery;
    private String[] mWsSubprotocols;
    private List<NameValuePair> mWsHeaders;
    private WebSocket.ConnectionHandler mWsHandler;
    private WebSocketOptions mOptions;
    private MessageIdPersister lastReceivedMesseges;

    private boolean refreshRequested;

    private Runnable disconnectIfWebSocketHandshakeTimeOut = new Runnable() {
        @Override
        public void run() {
            disconnectAndNotify(WebSocketConnectionHandler.CLOSE_HANDSHAKE_TIME_OUT, "Server Handshake Not Received After " + SERVER_HANDSHAKE_TIMEOUT + "ms");
        }
    };
    private static final int SERVER_HANDSHAKE_TIMEOUT = 7 * 1000;

    //pinging variables and constants
    private static final int[] PING_INTERVAL = {120 * 1000, 300 * 1000, 350 * 1000};
    private static final int PING_TIMEOUT = 5 * 1000;
    private static final String PING_DATA_PREFIX = String.valueOf((int) (Math.random() * 9999));
    private int currentPingId = 0;
    private int prevSuccessfulPings;
    private long lastPingSetTime; //last time a ping was set to be sent delayed

    private boolean restartInProgress;

    private Runnable sendPing = new Runnable() {
        @Override
        public void run() {
            BefrestImpl.startService(pushService, appContext, PushService.PING);
        }
    };

    private Runnable releaseConnectWakeLock = new Runnable() {
        @Override
        public void run() {
            releaseConnectWakeLockIfNeeded();
        }
    };

    private Runnable restart = new Runnable() {
        @Override
        public void run() {
            disconnectAndNotify(WebSocketConnectionHandler.CLOSE_CONNECTION_NOT_RESPONDING, "connection did not respond to ping message after " + PING_TIMEOUT + "ms");
        }
    };

    private void sendPing() {
        if (mWriter != null) {
            BefLog.i(TAG, "Sending Ping ...");
            postDelayed(restart, PING_TIMEOUT);
            restartInProgress = true;
            currentPingId = (currentPingId + 1) % 5;
            String payload = PING_DATA_PREFIX + currentPingId;
            mWriter.forward(new WebSocketMessage.Ping(payload.getBytes(Charset.defaultCharset())));
        } else BefLog.e(TAG, "could not send ping! writer is null");
    }

    private void onPong(String pongData) {
        boolean isValid = isValidPong(pongData);
        BefLog.i(TAG, "onPong(" + pongData + ") " + (isValid ? "valid" : "invalid!"));
        if (!isValid) return;
        cancelUpcommingRestart();
        prevSuccessfulPings++;
        if (getPrefs(appContext).getString(PREF_SERVICE_CHOOSER, null) == null)
            setNextPingToSendInFuture();
        notifyConnectionRefreshedIfNeeded();
    }

    private boolean isValidPong(String pongData) {
        return (PING_DATA_PREFIX + currentPingId).equals(pongData);
    }

    private void cancelFuturePing() {
        BefLog.i(TAG, "cancelFuturePing()");
        removeCallbacks(sendPing);
        cancelKeepPingingAlarm();
    }

    private void cancelUpcommingRestart() {
        BefLog.i(TAG, "cancelUpcommingRestart()");
        removeCallbacks(restart);
        restartInProgress = false;
    }

    private int getPingInterval() {
        return PING_INTERVAL[prevSuccessfulPings < PING_INTERVAL.length ? prevSuccessfulPings : PING_INTERVAL.length - 1];
    }

    private void revisePinging() {
        if (restartInProgress || System.currentTimeMillis() - lastPingSetTime < getPingInterval() / 2)
            return;
        prevSuccessfulPings++;
        if (getPrefs(appContext).getString(PREF_SERVICE_CHOOSER, null) == null)
            setNextPingToSendInFuture();
        BefLog.i(TAG, "BefrestImpl Pinging Revised");
    }

    private void setNextPingToSendInFuture() {
        setNextPingToSendInFuture(getPingInterval());
    }

    private void setNextPingToSendInFuture(int interval) {
        BefLog.i(TAG, "setNextPingToSendInFuture()  interval : " + interval);
        lastPingSetTime = System.currentTimeMillis();
        postDelayed(sendPing, interval);
        setKeepPingingAlarm(interval);
    }


    BefrestConnection(Context context, Looper looper, WebSocket.ConnectionHandler wsHandler, String url, List<NameValuePair> headers) {
        super(looper);
        this.mLooper = looper;
        this.mWsHandler = wsHandler;
        this.appContext = context.getApplicationContext();
        parseWebsocketUri(url, headers);
        pushService = ((BefrestInvocHandler) Proxy.getInvocationHandler(BefrestFactory.getInternalInstance(appContext))).obj.pushService;
        lastReceivedMesseges = new MessageIdPersister(appContext);
        BefLog.i(TAG, "lastReceivedMessages: " + lastReceivedMesseges);
    }

    private void setKeepPingingAlarm(int pingDelay) {
        int delay = (pingDelay * 2) + 60000;
        AlarmManager alarmMgr = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(appContext, pushService).putExtra(PushService.KEEP_PINGING, true);
        PendingIntent pi = PendingIntent.getService(appContext, BefrestImpl.KEEP_PINGING_ALARM_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        long triggerAtMillis = SystemClock.elapsedRealtime() + delay;
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pi);
        BefLog.i(TAG, "KeepPinging alarm set for " + delay + " ms");
    }

    private void cancelKeepPingingAlarm() {
        AlarmManager alarmMgr = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(appContext, pushService).putExtra(PushService.KEEP_PINGING, true);
        PendingIntent pi = PendingIntent.getService(appContext, BefrestImpl.KEEP_PINGING_ALARM_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (pi == null)
            reportNullPendingIntent();
        else {
            alarmMgr.cancel(pi);
            pi.cancel();
        }
        BefLog.i(TAG, "KeepPinging alarm canceled");
    }

    private void reportNullPendingIntent() {

    }

    @Override
    public void handleMessage(Message msg) {
        try {
            if (msg.obj instanceof WebSocketMessage.Message) {
                //msg from reader and writer
                handleMsgFromReaderWriter(((WebSocketMessage.Message) msg.obj));
            } else if (msg.obj instanceof BefrestEvent) {
                //msg from push service
                handleBefrestEvent(((BefrestEvent) msg.obj));
            }
        } catch (Throwable t) {
//            BefrestImpl.sendCrash(t.toString());
            BefLog.e(TAG, "unExpected Exception!");

            throw t;
        }
    }

    void forward(Object message) {
        Message msg = obtainMessage();
        msg.obj = message;
        sendMessage(msg);
    }

    private boolean isNewMessage(String msgId) {
        return !lastReceivedMesseges.contains(msgId);
    }

    private void sendAck(String ack) {
        try {
            if (mWriter != null) {
                mWriter.forward(new WebSocketMessage.TextMessage(ack));
                BefLog.i(TAG, "Ack sent : " + ack);
            } else {
                BefLog.i(TAG, "Could not send ack as mWriter is null (befrest is disconnected before we send ack message)");
            }
        } catch (Exception e) {
//            BefrestImpl.sendCrash(e.getCause().getMessage());
        }
    }
    private void handleMsgFromReaderWriter(WebSocketMessage.Message msg) {
        BefLog.i(TAG, msg.toString());
        if (msg instanceof WebSocketMessage.TextMessage) {
            WebSocketMessage.TextMessage textMessage = (WebSocketMessage.TextMessage) msg;
            revisePinging();
            BefLog.i(TAG, "rawMsg: " + textMessage.mPayload);
            BefrestMessage bmsg = new BefrestMessage(appContext, textMessage.mPayload);
            if (bmsg.isCorrupted)
                return;
            if (bmsg.msgId != null && bmsg.type != BefrestMessage.MsgType.BATCH && bmsg.type != BefrestMessage.MsgType.PONG) {
                sendAck(bmsg.getAckMessage());
                if (isNewMessage(bmsg.msgId)) {
                    lastReceivedMesseges.add(bmsg.msgId);
                    mWsHandler.onBefrestMessage(bmsg);
                    lastReceivedMesseges.save();
                }
            } else
                mWsHandler.onBefrestMessage(bmsg);
        } else if (msg instanceof WebSocketMessage.RawTextMessage) {

            WebSocketMessage.RawTextMessage rawTextMessage = (WebSocketMessage.RawTextMessage) msg;

            mWsHandler.onRawTextMessage(rawTextMessage.mPayload);

        } else if (msg instanceof WebSocketMessage.BinaryMessage) {

            WebSocketMessage.BinaryMessage binaryMessage = (WebSocketMessage.BinaryMessage) msg;
            mWsHandler.onBinaryMessage(binaryMessage.mPayload);

        } else if (msg instanceof WebSocketMessage.Ping) {

            WebSocketMessage.Ping ping = (WebSocketMessage.Ping) msg;
            BefLog.i(TAG, "WebSockets Ping received");

            // reply with Pong
            WebSocketMessage.Pong pong = new WebSocketMessage.Pong();
            pong.mPayload = ping.mPayload;
            if (mWriter != null) mWriter.forward(pong);

        } else if (msg instanceof WebSocketMessage.Pong) {
            WebSocketMessage.Pong pong = (WebSocketMessage.Pong) msg;
            onPong(new String(pong.mPayload, Charset.defaultCharset()));

        } else if (msg instanceof WebSocketMessage.Close) {

            WebSocketMessage.Close close = (WebSocketMessage.Close) msg;
            BefLog.i(TAG, "WebSockets Close received (" + close.mCode + " - " + close.mReason + ")");
            final int closeCode = (close.mCode == 1000) ? WebSocket.ConnectionHandler.CLOSE_NORMAL : WebSocket.ConnectionHandler.CLOSE_CONNECTION_LOST;
            disconnectAndNotify(closeCode, close.mReason);
        } else if (msg instanceof WebSocketMessage.ServerHandshake) {
            BefLog.i(TAG, "ServerHandShake");
            WebSocketMessage.ServerHandshake serverHandshake = (WebSocketMessage.ServerHandshake) msg;
            BefLog.i(TAG, "opening handshake received");
            removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);

            if (serverHandshake.mSuccess) {
                try {
                    mWsHandler.onOpen();
                } catch (Exception e) {
                    BefLog.e(TAG, e);
//                    BefrestImpl.sendCrash(e.getCause().getMessage());

                }
                postDelayed(releaseConnectWakeLock, 2000);
                notifyConnectionRefreshedIfNeeded();
                prevSuccessfulPings = 0;
                if (getPrefs(appContext).getString(PREF_SERVICE_CHOOSER, null) == null)
                    setNextPingToSendInFuture();
            } else {
                BefLog.w(TAG, "could not call onOpen() .. serverHandshake was not successful");
            }

        } else if (msg instanceof WebSocketMessage.ConnectionLost) {
            @SuppressWarnings("unused")
            WebSocketMessage.ConnectionLost connnectionLost = (WebSocketMessage.ConnectionLost) msg;

            disconnectAndNotify(WebSocketConnectionHandler.CLOSE_CONNECTION_LOST, "WebSockets connection lost");
        } else if (msg instanceof WebSocketMessage.ProtocolViolation) {

            @SuppressWarnings("unused")
            WebSocketMessage.ProtocolViolation protocolViolation = (WebSocketMessage.ProtocolViolation) msg;
            disconnectAndNotify(WebSocketConnectionHandler.CLOSE_PROTOCOL_ERROR, "WebSockets protocol violation");
        } else if (msg instanceof WebSocketMessage.Error) {

            WebSocketMessage.Error error = (WebSocketMessage.Error) msg;
            BefLog.e(TAG, error.mException);
            disconnectAndNotify(WebSocketConnectionHandler.CLOSE_INTERNAL_ERROR, "WebSockets internal error (" + error.mException.toString() + ")");

        } else if (msg instanceof WebSocketMessage.ServerError) {

            WebSocketMessage.ServerError error = (WebSocketMessage.ServerError) msg;
            int errCode = WebSocketConnectionHandler.CLOSE_SERVER_ERROR;
            if (error.mStatusCode == 401)
                errCode = WebSocketConnectionHandler.CLOSE_UNAUTHORIZED;
            disconnectAndNotify(errCode, "Server error " + error.mStatusCode + " (" + error.mStatusMessage + ")");
        }
    }

    private void disconnectAndNotify(int code, String reason) {
        BefLog.i(TAG, "disconnectAndNotify:" + code + " , " + reason);
        disconnect();
        mWsHandler.onClose(code, reason);
        releaseConnectWakeLockIfNeeded();
    }

    private void handleBefrestEvent(BefrestEvent e) {
        switch (e.type) {
            case CONNECT:
                connect();
                break;
            case DISCONNECT:
                disconnect();
                break;
            case STOP:
                mLooper.quit();
                break;
            case REFRESH: {
                BefLog.i(TAG, "handleBefrestEvent: " + getPrefs(appContext).getString(PREF_SERVICE_CHOOSER, null));
                if (getPrefs(appContext).getString(PREF_SERVICE_CHOOSER, null) == null) {
                    BefLog.i(TAG, "handleBefrestEvent: ");
                    refresh();
                }
                break;
            }
            case PING:
                sendPing();
                break;
        }
    }



    private void refresh() {
        BefLog.i(TAG, "refresh: ");
        refreshRequested = true;
        if (isConnected()) {
//            prevSuccessfulPings = 0; seems illogical
            cancelFuturePing();
            cancelUpcommingRestart();
            if (getPrefs(appContext).getString(PREF_SERVICE_CHOOSER, null) == null) {
                setNextPingToSendInFuture(0);
            }

        } else {
            BefLog.i(TAG, "refresh received when socket is not connected. will connect...");
            connect();
        }
    }

    private void notifyConnectionRefreshedIfNeeded() {
        if (refreshRequested) {
            refreshRequested = false;
            mWsHandler.onConnectionRefreshed();
        }
    }

    private void connect() {
        BefLog.i(TAG, "--------------------------connect()_START-----------------------------");
        if (isConnected()) {
            BefLog.i(TAG, "already connected!");
        } else if (appContext != null && !BefrestImpl.Util.isConnectedToInternet(appContext)) {
            BefLog.i(TAG, "no internet connection!");
        } else {
            acquireConnectWakeLockIfPossible();
            waitABit();
            try {
                mTransportChannel = createSocket();
                if (isConnected()) {
                    BefLog.i(TAG, "connect: here is connect");
                    createReader();
                    createWriter();
                    startWebSocketHandshake();
                    postDelayed(disconnectIfWebSocketHandshakeTimeOut, SERVER_HANDSHAKE_TIMEOUT);
                } else {
                    disconnectAndNotify(WebSocketConnectionHandler.CLOSE_CANNOT_CONNECT, "Could not connect to WebSocket server");
                }
            } catch (IOException e) {
                BefLog.e(TAG, e);
                disconnectAndNotify(WebSocketConnectionHandler.CLOSE_CANNOT_CONNECT, e.getMessage());
//                BefrestImpl.sendCrash(e.getCause().getMessage());
            } catch (Exception ex) {
                BefLog.e(TAG, ex);
//                BefrestImpl.sendCrash(ex.getCause().getMessage());
                disconnectAndNotify(WebSocketConnectionHandler.CLOSE_CANNOT_CONNECT, ex.getMessage());
            } catch (AssertionError e) {
                if (isAndroidGetsocknameError(e)) {
//                    BefrestImpl.sendCrash(e.getCause().getMessage());
                    disconnectAndNotify(WebSocketConnectionHandler.CLOSE_CANNOT_CONNECT, e.getMessage());
                } else
                    throw e;
            }
        }
        BefLog.i(TAG, "--------------------------connect()_END--------------------------");
    }

    private void waitABit() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
//            BefrestImpl.sendCrash(e.getCause().getMessage());
        }
    }

    private void startWebSocketHandshake() {
        WebSocketMessage.ClientHandshake hs = new WebSocketMessage.ClientHandshake(
                mWsHost + ":" + mWsPort);
        hs.mPath = mWsPath;
        hs.mQuery = mWsQuery;
        hs.mSubprotocols = mWsSubprotocols;
        hs.mHeaderList = mWsHeaders;
        mWriter.forward(hs);
    }

    private Socket createSocket() throws IOException {
        Socket soc;
        if (mWsScheme.equals("wss")) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
//                SSLSocket secSoc = (SSLSocket) factory.createSocket(mWsHost, mWsPort);
            SSLSocket secSoc = (SSLSocket) factory.createSocket();
            secSoc.setUseClientMode(true);
            secSoc.connect(new InetSocketAddress(mWsHost, mWsPort), mOptions.getSocketConnectTimeout());
//                secSoc.setSoTimeout(mOptions.getSocketReceiveTimeout());
            secSoc.setTcpNoDelay(mOptions.getTcpNoDelay());
            secSoc.addHandshakeCompletedListener(new HandshakeCompletedListener() {
                public void handshakeCompleted(HandshakeCompletedEvent event) {
                    BefLog.i(TAG, "ssl handshake completed");
                }
            });
            soc = secSoc;
        } else
            soc = new Socket(mWsHost, mWsPort);
        return soc;
    }

    /**
     * Create WebSockets background writer.
     */
    private void createWriter() {
        mWriterThread = new HandlerThread("WebSocketWriter");
        mWriterThread.start();
        mWriter = new WebSocketWriter(mWriterThread.getLooper(), this, mTransportChannel, mOptions, appContext);
        BefLog.i(TAG, "WS writer created and started");
    }


    /**
     * Create WebSockets background reader.
     */
    private void createReader() {
        mReader = new WebSocketReader(this, mTransportChannel, mOptions, "WebSocketReader", appContext);
        mReader.start();
        BefLog.i(TAG, "WS reader created and started");
    }


    private void parseWebsocketUri(String wsUri, List<NameValuePair> headers) {
        try {
            URI mWsUri = new URI(wsUri);

            mWsScheme = mWsUri.getScheme();

            if (mWsUri.getPort() == -1) {
                if (mWsScheme.equals("ws")) mWsPort = 80;
                else mWsPort = 443;
            } else mWsPort = mWsUri.getPort();
            mWsHost = mWsUri.getHost();

            if (mWsUri.getRawPath() == null || mWsUri.getRawPath().equals("")) mWsPath = "/";
            else mWsPath = mWsUri.getRawPath();

            if (mWsUri.getRawQuery() == null || mWsUri.getRawQuery().equals(""))
                mWsQuery = null;
            else mWsQuery = mWsUri.getRawQuery();
        } catch (URISyntaxException e) {
            //should not come here
//            BefrestImpl.sendCrash(e.getCause().getMessage());
        }
        mWsSubprotocols = null;
        mWsHeaders = headers;
        mOptions = new WebSocketOptions();
    }

    private void disconnect() {
        BefLog.i(TAG, "--------------------------disconnect()_START--------------------");
        removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);
        cancelFuturePing();
        cancelUpcommingRestart();
        if (mReader != null) {
            mReader.quit();
        } else BefLog.i(TAG, "mReader was null");
        if (mWriter != null) {
            mWriter.forward(new WebSocketMessage.Quit());
        } else
            BefLog.i(TAG, "mWriter was null");
        try {
            if (mTransportChannel != null) {
                try {
                    mTransportChannel.close();
                    BefLog.i(TAG, "mTranslateChannel closed");
                } catch (IOException e) {
                    BefLog.e(TAG, e);
//                    BefrestImpl.sendCrash(e.getCause().getMessage());
                } catch (AssertionError e) {
                    if (isAndroidGetsocknameError(e)) {
                        BefLog.e(TAG, e);
//                        BefrestImpl.sendCrash(e.getCause().getMessage());
                    } else throw e;
                }
            } else {
                BefLog.i(TAG, "mTransportChannel was NULL");
            }
            if (mWriterThread != null) {
                mWriterThread.join(1000);
                BefLog.i(TAG, "mWriterThread joined");
            }
            if (mReader != null) {
                mReader.join(1000);
                BefLog.i(TAG, "mReader joined");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
//            BefrestImpl.sendCrash(e.getCause().getMessage());
        }
        mReader = null;
        mWriter = null;
        mWriterThread = null;
        mTransportChannel = null;
        BefLog.i(TAG, "--------------------------disconnect()_END--------------------");
    }

    private boolean isConnected() {
        boolean res = mTransportChannel != null && mTransportChannel.isConnected() && !mTransportChannel.isClosed();
        return res;
    }

    @SuppressLint("InvalidWakeLockTag")
    private void acquireConnectWakeLockIfPossible() {
        removeCallbacks(releaseConnectWakeLock);
        if (BefrestInternal.Util.isWakeLockPermissionGranted(appContext)) {
            if (connectWakelock != null) {
                if (connectWakelock.isHeld())
                    return;
            } else {
                PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                connectWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, connectWakeLockName);
                connectWakelock.setReferenceCounted(false);
            }
            connectWakelock.acquire(10 * 60 * 1000L /*10 minutes*/);
            BefLog.i(TAG, "connectWakeLock acquired.");
        } else
            BefLog.i(TAG, "could not acquire connect wakelock. (permission not granted)");
    }

    private void releaseConnectWakeLockIfNeeded() {
        if (BefrestInternal.Util.isWakeLockPermissionGranted(appContext)) {
            if (connectWakelock != null && connectWakelock.isHeld()) {
                connectWakelock.release();
                BefLog.i(TAG, "connectWakeLock released manually");
            }
        }
    }

    /**
     * Returns true if {@code e} is due to a firmware bug fixed after Android 4.2.2.
     * https://code.google.com/p/android/issues/detail?id=54072
     */
    private static boolean isAndroidGetsocknameError(AssertionError e) {
        return e.getCause() != null && e.getMessage() != null
                && e.getMessage().contains("getsockname failed");
    }


}
