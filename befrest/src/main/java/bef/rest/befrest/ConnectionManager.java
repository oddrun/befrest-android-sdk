package bef.rest.befrest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

import javax.net.ssl.SSLException;

import bef.rest.befrest.befrest.Befrest;
import bef.rest.befrest.befrest.BefrestEvent;
import bef.rest.befrest.befrest.BefrestMessage;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.MessageIdPersister;
import bef.rest.befrest.utils.UrlConnection;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.websocket.SocketCallBacks;
import bef.rest.befrest.websocket.SocketHelper;
import bef.rest.befrest.websocket.WebSocketMessage;

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
import static bef.rest.befrest.utils.SDKConst.PING;
import static bef.rest.befrest.utils.SDKConst.PING_DATA_PREFIX;
import static bef.rest.befrest.utils.SDKConst.PING_INTERVAL;
import static bef.rest.befrest.utils.SDKConst.PING_TIMEOUT;
import static bef.rest.befrest.utils.SDKConst.PING_TIME_OUT_MESSAGE;
import static bef.rest.befrest.utils.Util.acquireConnectWakeLock;

public class ConnectionManager extends Handler {

    private String TAG = ConnectionManager.class.getSimpleName();
    private Looper looper;
    private SocketHelper socketHelper;
    private Context appContext;
    private int prevSuccessfullPings;
    private SocketCallBacks socketCallBacks;
    private boolean refreshRequested;
    private boolean restartInProgress;
    private int currentPingId = 0;
    private MessageIdPersister lastReceivedMessages;
    private PowerManager.WakeLock wakeLock;
    private long lastPingSetTime;

    ConnectionManager(Looper looper, SocketCallBacks wsHandler) {
        super(looper);
        this.looper = looper;
        this.socketCallBacks = wsHandler;
        this.appContext = Befrest.getInstance().getContext().getApplicationContext();
        lastReceivedMessages = new MessageIdPersister();
        socketHelper = new SocketHelper(this);
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
            serverHandshakeMessage();
        else if (msg instanceof WebSocketMessage.Pong)
            serverPongMessage(msg);
        else if (msg instanceof WebSocketMessage.TextMessage)
            serverTextMessage(msg);
        else if (msg instanceof WebSocketMessage.Close)
            serverCloseMessage(msg);
        else if (msg instanceof WebSocketMessage.ServerError)
            serverErrorMessage(msg);
        else if (msg instanceof WebSocketMessage.ConnectionLost)
            disconnectAndNotify(CLOSE_CONNECTION_LOST, "WebSockets connection lost");
        else if (msg instanceof WebSocketMessage.ProtocolViolation)
            disconnectAndNotify(CLOSE_PROTOCOL_ERROR, "WebSockets protocol violation");
        else if (msg instanceof WebSocketMessage.Error)
            disconnectAndNotify(CLOSE_INTERNAL_ERROR, "WebSockets internal error");
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
        final int closeCode = (close.mCode == 1000) ? CLOSE_NORMAL : CLOSE_CONNECTION_LOST;
        disconnectAndNotify(closeCode, close.mReason);
    }

    private void serverTextMessage(WebSocketMessage.Message msg) {
        BefrestLog.v(TAG, "ServerTextMessage: message received from server");
        revisePinging();
        WebSocketMessage.TextMessage textMessage = (WebSocketMessage.TextMessage) msg;
        BefrestMessage befrestMessage = new BefrestMessage(appContext, textMessage.payload);
        if (befrestMessage.isCorrupted())
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
                sendAckToServer(befrestMessage.getAckMessage());
                if (isNewMessage(befrestMessage.getMsgId())) {
                    lastReceivedMessages.add(befrestMessage.getMsgId());
                    socketCallBacks.onBefrestMessage(befrestMessage);
                    lastReceivedMessages.save();
                }
                break;
        }
    }

    private void sendAckToServer(String ackMessage) {
        BefrestLog.v(TAG, "send Ack Message " + ackMessage + " ToServer");
        socketHelper.writeOnWebSocket(new WebSocketMessage.AckMessage(ackMessage));
    }

    private void serverHandshakeMessage() {
        BefrestLog.i(TAG, "ServerHandshakeMessage: is complete");
        socketCallBacks.onOpen();
        socketCallBacks.changeConnection(true);
        removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);
        post(releaseWakeLock);
        prevSuccessfullPings = 0;
        if (Befrest.getInstance().isServiceRunning())
            setNextPingToSendInFuture();
    }

    private void serverPongMessage(WebSocketMessage.Message msg) {
        BefrestLog.i(TAG, "ServerPongMessage: server response to Ping Message");
        WebSocketMessage.Pong pong = (WebSocketMessage.Pong) msg;
        Pong(new String(pong.mPayload, Charset.defaultCharset()));
    }

    private void Pong(String pongData) {
        BefrestLog.v(TAG, "Pong: process on pongData Message");
        boolean isValid = isValidPong(pongData);
        if (!isValid) return;
        BefrestLog.v(TAG, "pong data with data : " + pongData + " is valid");
        cancelRestart();
        prevSuccessfullPings++;
        if (Befrest.getInstance().isServiceRunning())
            setNextPingToSendInFuture();
        notifyConnectionRefreshed();
    }

    private void notifyConnectionRefreshed() {
        socketCallBacks.onConnectionRefreshed();
    }

    private boolean isValidPong(String pongData) {
        return (PING_DATA_PREFIX + currentPingId).equals(pongData);
    }

    private void setNextPingToSendInFuture() {
        setNextPingToSendInFuture(getPingInterval());
    }

    private int getPingInterval() {
        return PING_INTERVAL[prevSuccessfullPings < PING_INTERVAL.length ? prevSuccessfullPings : PING_INTERVAL.length - 1];
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
                disconnect();
                break;
            case STOP:
                looper.quit();
                break;
        }
    }

    private void refreshConnection() {
        BefrestLog.v(TAG, "Refresh Connection ");
        if (!refreshRequested) {
            if (socketHelper != null && socketHelper.isSocketConnected()) {
                refreshRequested = true;
                cancelRestart();
                setNextPingToSendInFuture(0);
            } else connectToServer();
        }
    }

    private void pingServer() {
        BefrestLog.v(TAG, "Ping Server Request");
        lastPingSetTime = System.currentTimeMillis();
        setupRestart();
        currentPingId = (currentPingId + 1) % 5;
        String payload = PING_DATA_PREFIX + currentPingId;
        socketHelper.writeOnWebSocket(
                new WebSocketMessage.Ping(payload.getBytes(Charset.defaultCharset()))
        );
    }

    private void revisePinging() {
        if (restartInProgress || System.currentTimeMillis() - lastPingSetTime < getPingInterval() / 2)
            return;
        prevSuccessfullPings++;
        if (Befrest.getInstance().isServiceRunning())
            setNextPingToSendInFuture();
        BefrestLog.v(TAG, "revisePinging Pinging Revised");
    }

    private void setupRestart() {
        BefrestLog.v(TAG, "setupRestart: ");
        postDelayed(restart, PING_TIMEOUT);
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
            wakeLock = acquireConnectWakeLock(wakeLock);
            if (!socketHelper.isSocketConnected()) {
                if (Util.isConnectedToInternet(appContext)) {
                    socketHelper.createSocket();
                    socketHelper.startWebSocketHandshake();
                    postDelayed(disconnectIfWebSocketHandshakeTimeOut, 7000);
                } else {
                    BefrestLog.w(TAG, "Internet connection is not available");
                }
            } else {
                BefrestLog.w(TAG, "Befrest connection already established");
            }
        } catch (SocketTimeoutException | SSLException e) {
            // todo: catch all possible SSL related exceptions
            BefrestLog.i(TAG, "Switch to ws and port 80");
            UrlConnection.getInstance().setScheme("ws");
            UrlConnection.getInstance().setPort(80);
            disconnectAndNotify();
            e.printStackTrace();
        } catch (IOException e) {
            disconnectAndNotify();
            e.printStackTrace();
        }
    }

    private boolean isNewMessage(String msgId) {
        return !lastReceivedMessages.contains(msgId);
    }

    private void disconnectAndNotify() {
        disconnectAndNotify(CLOSE_CANNOT_CONNECT, "cannot create Socket writer or reader");
    }

    private void disconnectAndNotify(int code, String reason) {
        socketCallBacks.onClose(code, reason);
        disconnect();
    }

    private void disconnect() {
        BefrestLog.w(TAG, "disconnect start");
        socketCallBacks.changeConnection(false);
        removeCallbacks(disconnectIfWebSocketHandshakeTimeOut);
        cancelRestart();
        try {
            socketHelper.freeReader();
            socketHelper.freeWriter();
            socketHelper.freeSocket();
            socketHelper.joinWriter();
            socketHelper.joinWriter();
            socketHelper.joinReader();
        } catch (InterruptedException ignored) {

        } catch (Exception ignored) {

        }
        BefrestLog.i(TAG, "disconnect finish");
    }

    private Runnable sendPing = () -> Befrest.getInstance().startService(PING);
    private Runnable restart = () -> disconnectAndNotify(CLOSE_CONNECTION_NOT_RESPONDING, PING_TIME_OUT_MESSAGE);
    private Runnable disconnectIfWebSocketHandshakeTimeOut = () ->
            disconnectAndNotify(CLOSE_HANDSHAKE_TIME_OUT, HANDSHAKE_TIMEOUT_MESSAGE);

    private Runnable releaseWakeLock = () -> {
        BefrestLog.i(TAG, "release WakeLock");
        if (wakeLock != null) {
            wakeLock.release();
            BefrestLog.i(TAG, "wakeLock successfully released");
        }
    };
}
