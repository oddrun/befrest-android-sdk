package bef.rest.befrest;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import bef.rest.befrest.autobahnLibrary.SocketCallBacks;
import bef.rest.befrest.autobahnLibrary.WebSocketMessage;
import bef.rest.befrest.befrest.BefrestConnectionMode;
import bef.rest.befrest.befrest.BefrestEvent;
import bef.rest.befrest.befrest.BefrestMessage;
import bef.rest.befrest.utils.AnalyticsType;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.BefrestThreadFactory;
import bef.rest.befrest.utils.MessageIdPersister;
import bef.rest.befrest.utils.ReportManager;
import bef.rest.befrest.utils.UrlConnection;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.utils.WatchSdk;
import bef.rest.befrest.websocket.SocketHelper;

import static bef.rest.befrest.utils.BefrestPreferences.PREF_HANDSHAKE_TIMEOUT;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_PING_INTERVAL;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_PING_TIMEOUT;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;
import static bef.rest.befrest.utils.SDKConst.CLOSE_CANNOT_CONNECT;
import static bef.rest.befrest.utils.SDKConst.CLOSE_CONNECTION_LOST;
import static bef.rest.befrest.utils.SDKConst.CLOSE_CONNECTION_NOT_RESPONDING;
import static bef.rest.befrest.utils.SDKConst.CLOSE_HANDSHAKE_TIME_OUT;
import static bef.rest.befrest.utils.SDKConst.CLOSE_INTERNAL_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_NORMAL;
import static bef.rest.befrest.utils.SDKConst.CLOSE_PROTOCOL_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_SERVER_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_UNAUTHORIZED;
import static bef.rest.befrest.utils.SDKConst.HANDSHAKE_TIMEOUT_MESSAGE;
import static bef.rest.befrest.utils.SDKConst.PING_TIME_OUT_MESSAGE;
import static bef.rest.befrest.utils.Util.acquireConnectWakeLock;
import static bef.rest.befrest.utils.Util.getRandomNumber;

public class ConnectionManager extends Handler {

    private static final int PING_ID_LIST_SIZE = 20;
    private String TAG = ConnectionManager.class.getSimpleName();
    private Looper looper;
    private SocketHelper socketHelper;
    private Context appContext;
    private int prevSuccessfulPings;
    private SocketCallBacks socketCallBacks;
    private boolean refreshRequested;
    private boolean restartInProgress;
    private MessageIdPersister lastReceivedMessages;
    private PowerManager.WakeLock wakeLock;
    private List<String> pingIdList = new ArrayList<>();
    private String pendingPingId = "";
    private int[] pingInterval;
    private int handShakeTimeOut;
    private int pingTimeOut;
    private Handler messageHandler;
    private Executor executor = new ThreadPoolExecutor(0, 1, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            new BefrestThreadFactory("BEFREST_REPORT_POOL"));
    private List<String> pendingAcks = new ArrayList<>();
    private boolean isSendingAck;


    ConnectionManager(Looper looper, SocketCallBacks socketCallback) {
        super(looper);
        if (!Befrest.getInstance().isBefrestInitialized())
            throw new RuntimeException("Befrest in not initialized yet call init method in Application class");
        this.looper = looper;
        this.socketCallBacks = socketCallback;
        this.appContext = Befrest.getInstance().getContext().getApplicationContext();
        lastReceivedMessages = new MessageIdPersister();
        socketHelper = new SocketHelper(this);
        pingInterval = getPingInterval();
        handShakeTimeOut = getHandShakeTimeOut();
        pingTimeOut = getPingTimeOut();
        HandlerThread messageHandlerThread = new HandlerThread(TAG);
        messageHandlerThread.start();
        messageHandler = new Handler(messageHandlerThread.getLooper());
    }

    void forward(Object message) {
        Message msg = obtainMessage();
        msg.obj = message;
        sendMessage(msg);
    }

    @Override
    public void handleMessage(Message msg) {
        Object message = msg.obj;
        if (message instanceof BefrestEvent)
            HandleBefrestEvent((BefrestEvent) message);
        if (message instanceof WebSocketMessage.Message)
            handleWebSocketMessage((WebSocketMessage.Message) message);
        super.handleMessage(msg);
    }

    private void handleWebSocketMessage(WebSocketMessage.Message msg) {
        BefrestLog.d(TAG, "HandleWebSocketMessage: " + msg.toString());
        if (msg instanceof WebSocketMessage.ServerHandshake)
            serverHandshakeMessage(msg);
        else if (msg instanceof WebSocketMessage.Pong)
            serverPongMessage(msg);
        else if (msg instanceof WebSocketMessage.TextMessage)
            serverTextMessage(msg);
        else if (msg instanceof WebSocketMessage.Close)
            serverCloseMessage(msg);
        else if (msg instanceof WebSocketMessage.ServerError)
            serverErrorMessage(msg);
        else if (msg instanceof WebSocketMessage.Redirect)
            followRedirectMessage(msg);
        else if (msg instanceof WebSocketMessage.ConnectionLost)
            disconnectAndNotify(CLOSE_CONNECTION_LOST, "WebSockets connection lost");
        else if (msg instanceof WebSocketMessage.ProtocolViolation)
            disconnectAndNotify(CLOSE_PROTOCOL_ERROR, "WebSockets protocol violation");
        else if (msg instanceof WebSocketMessage.Error)
            disconnectAndNotify(CLOSE_INTERNAL_ERROR, "WebSockets internal error");
    }

    private void followRedirectMessage(WebSocketMessage.Message msg) {
        WebSocketMessage.Redirect redirect = (WebSocketMessage.Redirect) msg;
        UrlConnection.getInstance().followRedirect(redirect.location);
        removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);
        try {
            socketHelper.freeSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connectToServer();
    }

    private void serverErrorMessage(WebSocketMessage.Message msg) {
        BefrestLog.e(TAG, "serverErrorMessage: ");
        WebSocketMessage.ServerError error = (WebSocketMessage.ServerError) msg;
        int errCode = CLOSE_SERVER_ERROR;
        if (error.mStatusCode == 401) errCode = CLOSE_UNAUTHORIZED;
        disconnectAndNotify(errCode, "Server error " + error.mStatusCode + " (" + error.mStatusMessage + ")");
    }

    private void serverCloseMessage(WebSocketMessage.Message msg) {
        BefrestLog.e(TAG, "ServerCloseMessage: ");
        WebSocketMessage.Close close = (WebSocketMessage.Close) msg;
        final int closeCode = (close.code == 1000) ? CLOSE_NORMAL : CLOSE_CONNECTION_LOST;
        disconnectAndNotify(closeCode, close.reason);
    }

    private void serverTextMessage(WebSocketMessage.Message msg) {
        BefrestLog.v(TAG, "ServerTextMessage: message received from server");
        WebSocketMessage.TextMessage textMessage = (WebSocketMessage.TextMessage) msg;
        BefrestMessage befrestMessage = new BefrestMessage(textMessage.payload);
        cancelFuturePing();
        pong(null);
        if (befrestMessage.isCorrupted() || befrestMessage.isConfigPush())
            return;
        switch (befrestMessage.getType()) {
            case BATCH:
                socketCallBacks.onBefrestMessage(befrestMessage);
                break;
            case PONG:
                return;
            case NORMAL:
            case GROUP:
            case TOPIC:
                pendingAcks.add(befrestMessage.getAckMessage());
                if (!isSendingAck) {
                    postDelayed(sendNextAck, 50);
                    isSendingAck = true;
                }
                if (isNewMessage(befrestMessage.getMsgId())) {
                    lastReceivedMessages.add(befrestMessage.getMsgId());
                    socketCallBacks.onBefrestMessage(befrestMessage);
                    removeCallbacks(saveMessageRunnable);
                    postDelayed(saveMessageRunnable,1000);
                }
                break;
        }
    }

    private void sendAckToServer(String ackMessage) {
        BefrestLog.v(TAG, "send Ack Message " + ackMessage + " ToServer");
        socketHelper.writeOnWebSocket(new WebSocketMessage.AckMessage(ackMessage));
    }

    private void serverHandshakeMessage(WebSocketMessage.Message msg) {
        WebSocketMessage.ServerHandshake message = (WebSocketMessage.ServerHandshake) msg;
        if (message.mSuccess) {
            BefrestLog.i(TAG, "ServerHandshakeMessage: is complete");
            socketCallBacks.onOpen();
            socketCallBacks.onChangeConnection(BefrestConnectionMode.CONNECTED, null);
            removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);
            post(releaseWakeLock);
            prevSuccessfulPings = 0;
            if (Befrest.getInstance().isServiceRunning())
                setNextPingToSendInFuture();
        } else {
            sendCacheReport();
            BefrestLog.i(TAG, "serverHandshakeMessage: error happen on ServerHandShake");
        }
    }

    private void serverPongMessage(WebSocketMessage.Message msg) {
        BefrestLog.i(TAG, "ServerPongMessage: server response to Ping Message");
        WebSocketMessage.Pong pong = (WebSocketMessage.Pong) msg;
        pong(new String(pong.payload, Charset.defaultCharset()));
    }

    private void pong(String pongData) {
        BefrestLog.v(TAG, "pong: process on pongData Message");
        if (pongData != null) {
            boolean isValid = isPongValid(pongData);
            if (!isValid) {
                WatchSdk.reportAnalytics(AnalyticsType.INVALID_PONG);
                return;
            }
            BefrestLog.v(TAG, "pong with data : " + pongData + " is valid");
            prevSuccessfulPings++;
        }
        pendingPingId = "";
        cancelRestart();
        if (Befrest.getInstance().isServiceRunning())
            setNextPingToSendInFuture();
        notifyConnectionRefreshed();
    }

    private void notifyConnectionRefreshed() {
        socketCallBacks.onConnectionRefreshed();
    }

    private boolean isPongValid(String pongData) {
        try {
            if (!pongData.equals(pendingPingId)) {
                pingIdList.remove(pongData);
                return false;
            }
            return pingIdList.remove(pongData);
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
        }
        return false;
    }

    private void setNextPingToSendInFuture() {
        setNextPingToSendInFuture(getNextPingInterval());
    }

    private int getNextPingInterval() {
        return pingInterval[prevSuccessfulPings < pingInterval.length ? prevSuccessfulPings : pingInterval.length - 1];
    }

    private void setNextPingToSendInFuture(int interval) {
        BefrestLog.v(TAG, "Send ping after " + interval + " ms");
        postDelayed(sendPing, interval);
    }

    private void HandleBefrestEvent(BefrestEvent message) {
        BefrestLog.v(TAG, "Handle Event :" + message);
        switch (message) {
            case CONNECT:
                connectToServer();
                break;
            case PING:
                pingServer();
                break;
            case REFRESH:
                if (Befrest.getInstance().isServiceRunning())
                    refreshConnection();
                break;
            case DISCONNECT:
                BefrestLog.i(TAG, "DISCONNECT");
                disconnect();
                break;
            case STOP:
                looper.quit();
                break;
            case TEST:
                sendSample();
        }
    }

    private void sendSample() {
        for (int i = 0; i <100 ; i++) {
            try {
                Thread.sleep(1000);
                socketHelper.writeOnWebSocket(new WebSocketMessage.AckMessage(i + "   : ljkdfgldkfdjdlkgjdfjklgdjgdkljgdfklfdjgdlkgjdgl;kd;jgd;kflgjdflkdfjgdlfkjgjdfdlh"));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void refreshConnection() {
        BefrestLog.v(TAG, "Refresh Connection ");
        if (!refreshRequested) {
            if (socketHelper != null && socketHelper.isSocketConnected()) {
                refreshRequested = true;
                cancelRestart();
                cancelFuturePing();
                setNextPingToSendInFuture(0);
            } else connectToServer();
        }
    }

    private void pingServer() {
        BefrestLog.v(TAG, "Ping Server Request");
        setupRestart();
        pendingPingId = generatePingId();
        socketHelper.writeOnWebSocket(
                new WebSocketMessage.Ping(String.valueOf(pendingPingId).getBytes(Charset.defaultCharset())));
    }

    private void cancelFuturePing() {
        removeCallbacks(sendPing);
    }

    private String generatePingId() {
        String pingId;
        do {
            pingId = getRandomNumber();
        } while (pingIdList.contains(pingId));
        if (pingIdList.size() >= PING_ID_LIST_SIZE)
            pingIdList.remove(0);
        pingIdList.add(pingId);
        BefrestLog.i(TAG, String.format(Locale.US,
                "new Ping id is %s and pingIdList size is %d", pingId, pingIdList.size()));
        return pingId;
    }

    private void setupRestart() {
        BefrestLog.v(TAG, "setupRestart: ");
        postDelayed(restart, pingTimeOut);
        restartInProgress = true;
    }

    private void cancelRestart() {
        BefrestLog.v(TAG, "cancel Restart");
        if (restartInProgress) {
            removeCallbacks(restart);
            restartInProgress = false;
        }
    }

    private void connectToServer() {
        BefrestLog.v(TAG, "connectToServer: ");
        try {
            if (!socketHelper.isSocketConnected()) {
                if (Util.isConnectedToInternet(appContext)) {
                    WatchSdk.reportAnalytics(AnalyticsType.TRY_TO_CONNECT);
                    wakeLock = acquireConnectWakeLock(wakeLock);
                    socketHelper.createSocket();
                    socketHelper.startWebSocketHandshake();
                    postDelayed(disconnectIfWebSocketHandshakeTimeOut, handShakeTimeOut);
                } else {
                    BefrestLog.w(TAG, "Internet connection is not available");
                }
            } else {
                BefrestLog.w(TAG, "Befrest connection already established");
            }
        } catch (SocketTimeoutException | SSLException | SocketException e) {
            socketHelper.fillNull();
            BefrestLog.i(TAG, "Switch to ws and port 80");
            UrlConnection.getInstance().setScheme("ws");
            UrlConnection.getInstance().setPort(80);
            disconnectAndNotify("exception thrown during socket creation");
            e.printStackTrace();
            WatchSdk.reportCrash(e, null);
        } catch (IOException e) {
            disconnectAndNotify(e.getMessage());
            e.printStackTrace();
            WatchSdk.reportCrash(e, null);
        }
    }

    private void sendCacheReport() {
        new ReportManager().executeOnExecutor(executor);
    }

    private boolean isNewMessage(String msgId) {
        return !lastReceivedMessages.contains(msgId);
    }

    private void disconnectAndNotify(String message) {
        socketCallBacks.onClose(CLOSE_CANNOT_CONNECT, message);
    }

    private void disconnectAndNotify(int code, String reason) {
        BefrestLog.i(TAG, reason);
        socketCallBacks.onClose(code, reason);
        disconnect(reason);
    }

    private void disconnect(Object... reason) {
        BefrestLog.w(TAG, "disconnect start");
        String res = null;
        if (reason.length > 0) {
            res = reason[0].toString();
        }
        removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);
        cancelRestart();
        pingIdList.clear();
        cancelFuturePing();
        try {
            if (socketHelper.isSocketHelperValid()) {
                BefrestLog.d(TAG, "disconnect: make free reader and writer");
                socketHelper.writeOnWebSocket(new WebSocketMessage.Quit());
                socketCallBacks.onChangeConnection(BefrestConnectionMode.DISCONNECTED, res);
                socketHelper.freeReader();
                socketHelper.freeWriter();
                socketHelper.freeSocket();
                socketHelper.joinWriter();
                socketHelper.joinWriter();
                socketHelper.joinReader();
                socketHelper.fillNull();
            } else
                BefrestLog.i(TAG, "reader already dead");
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
        }
        BefrestLog.i(TAG, "disconnect finish");
    }

    private int getPingTimeOut() {
        if (getPrefs() != null)
            return getPrefs().getInt(PREF_PING_TIMEOUT, 10_000);
        return 10_000;
    }

    private int getHandShakeTimeOut() {
        if (getPrefs() != null)
            return getPrefs().getInt(PREF_HANDSHAKE_TIMEOUT, 7_000);
        return 7_000;
    }

    private int[] getPingInterval() {
        int[] pi = {10_000, 15_000, 30_000, 40_000};
        if (getPrefs() != null) {
            String pingInterval = getPrefs().getString(PREF_PING_INTERVAL, null);
            if (pingInterval != null) {
                String[] split = pingInterval.split("-");
                if (split.length > 5)
                    for (int i = 0; i < pi.length; i++)
                        pi[i] = Integer.valueOf(split[i]);
            }
        }
        return pi;
    }

    private Runnable sendPing = () -> socketCallBacks.pingServer();
    private Runnable restart = () -> {
        if (pingIdList.size() > 2) {
            disconnectAndNotify(CLOSE_CONNECTION_NOT_RESPONDING, PING_TIME_OUT_MESSAGE);
            WatchSdk.reportAnalytics(AnalyticsType.CONNECTION_LOST, pingTimeOut, PING_TIME_OUT_MESSAGE);
        }
    };
    private Runnable disconnectIfWebSocketHandshakeTimeOut = () -> {
        WatchSdk.reportAnalytics(AnalyticsType.CANNOT_CONNECT, CLOSE_HANDSHAKE_TIME_OUT, HANDSHAKE_TIMEOUT_MESSAGE);
        disconnectAndNotify(CLOSE_HANDSHAKE_TIME_OUT, HANDSHAKE_TIMEOUT_MESSAGE);
    };
    private Runnable releaseWakeLock = () -> {
        BefrestLog.i(TAG, "release WakeLock");
        if (wakeLock != null) {
            wakeLock.release();
            BefrestLog.i(TAG, "wakeLock successfully released");
        }
    };

    private Runnable saveMessageRunnable = () -> {
        lastReceivedMessages.save();
        BefrestLog.i(TAG, "save Message Successfully");
    };

    private Runnable sendNextAck = new Runnable() {
        @Override
        public void run() {
            if (!pendingAcks.isEmpty()) {
                try {
                        socketHelper.writeOnWebSocket(new WebSocketMessage.
                                AckMessage(pendingAcks.remove(pendingAcks.size() - 1)));
                } catch (Exception ignored) {
                }
            }
            if (!pendingAcks.isEmpty()) {
                postDelayed(sendNextAck, 50);
                isSendingAck = true;
            } else {
                isSendingAck = false;
            }
        }
    };
}
