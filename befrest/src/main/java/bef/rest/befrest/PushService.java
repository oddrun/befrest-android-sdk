package bef.rest.befrest;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bef.rest.befrest.befrest.Befrest;
import bef.rest.befrest.befrest.BefrestContract;
import bef.rest.befrest.befrest.BefrestEvent;
import bef.rest.befrest.befrest.BefrestMessage;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.JobServiceManager;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.websocket.SocketCallBacks;

import static bef.rest.befrest.utils.SDKConst.BATCH_MODE_TIMEOUT;
import static bef.rest.befrest.utils.SDKConst.BEFREST_CONNECTED;
import static bef.rest.befrest.utils.SDKConst.BEFREST_CONNECTION_CHANGED;
import static bef.rest.befrest.utils.SDKConst.CLOSE_CANNOT_CONNECT;
import static bef.rest.befrest.utils.SDKConst.CLOSE_CONNECTION_LOST;
import static bef.rest.befrest.utils.SDKConst.CLOSE_CONNECTION_NOT_RESPONDING;
import static bef.rest.befrest.utils.SDKConst.CLOSE_HANDSHAKE_TIME_OUT;
import static bef.rest.befrest.utils.SDKConst.CLOSE_INTERNAL_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_NORMAL;
import static bef.rest.befrest.utils.SDKConst.CLOSE_PROTOCOL_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_SERVER_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_UNAUTHORIZED;
import static bef.rest.befrest.utils.SDKConst.CONNECT;
import static bef.rest.befrest.utils.SDKConst.CONNECTION_REFRESHED;
import static bef.rest.befrest.utils.SDKConst.KEY_MESSAGE_PASSED;
import static bef.rest.befrest.utils.SDKConst.NETWORK_CONNECTED;
import static bef.rest.befrest.utils.SDKConst.NETWORK_DISCONNECTED;
import static bef.rest.befrest.utils.SDKConst.OREO_SDK_INT;
import static bef.rest.befrest.utils.SDKConst.PING;
import static bef.rest.befrest.utils.SDKConst.PUSH;
import static bef.rest.befrest.utils.SDKConst.REFRESH;
import static bef.rest.befrest.utils.SDKConst.RETRY;
import static bef.rest.befrest.utils.SDKConst.SDK_INT;
import static bef.rest.befrest.utils.SDKConst.SERVICE_STOPPED;
import static bef.rest.befrest.utils.SDKConst.TIME_PER_MESSAGE_IN_BATH_MODE;
import static bef.rest.befrest.utils.SDKConst.UNAUTHORIZED;
import static bef.rest.befrest.utils.SDKConst.prevAuthProblems;
import static bef.rest.befrest.utils.SDKConst.prevFailedConnectTries;
import static bef.rest.befrest.utils.Util.getIntentEvent;
import static bef.rest.befrest.utils.Util.getNextReconnectInterval;
import static bef.rest.befrest.utils.Util.getSendOnAuthorizeBroadcastDelay;

public class PushService extends Service implements SocketCallBacks {

    private static String TAG = PushService.class.getName();
    private HandlerThread befrestHandler;
    private ConnectionManager connectionManager;
    private Handler mainThreadHandler;
    private Handler messageHandler;
    private HandlerThread messageHandlerThread;
    private boolean retryInProgress;
    private List<BefrestMessage> receivedMessages = new ArrayList<>();
    private BefrestContract befrestContract;
    private boolean isBachReceiveMode;
    private int batchSize;

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public void onCreate() {
        befrestContract = new BefrestContract();
        mainThreadHandler = new Handler();
        befrestHandler = new HandlerThread("BefrestConnectionThread");
        befrestHandler.start();
        messageHandlerThread = new HandlerThread("BefrestMessageHandler");
        messageHandlerThread.start();
        connectionManager = new ConnectionManager(befrestHandler.getLooper(), this);
        messageHandler = new Handler(messageHandlerThread.getLooper());
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BefrestLog.v(TAG, "onStartCommand : with Intent : " + getIntentEvent(intent));
        handleEvent(getIntentEvent(intent));
        return SDK_INT >= OREO_SDK_INT ? START_NOT_STICKY : START_STICKY;
    }

    public void handleEvent(String event) {
        switch (event) {
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
                cancelRetryMode();
                connectionManager.forward(BefrestEvent.DISCONNECT);
                break;
            case SERVICE_STOPPED:
                handleServiceStop();
                break;
            case PING:
                connectionManager.forward(BefrestEvent.PING);
                break;
            default:
                connectIfNetworkAvailable();
        }
    }

    private void handleServiceStop() {
        if (Befrest.getInstance().isBefrestStart())
            if (!retryInProgress)
                connectIfNetworkAvailable();
            else
                stopSelf();
    }

    private void scheduleReconnect() {
        boolean hasNetworkConnection = Util.isConnectedToInternet(this);
        if (retryInProgress || !hasNetworkConnection)
            return;
        cancelRetryMode();
        setupRetryMode(getNextReconnectInterval());
        prevFailedConnectTries++;
        BefrestLog.w(TAG, "schedule to Reconnect after " + getNextReconnectInterval());
    }

    private void handleAuthorizeProblem() {
        onAuthorizeProblem();
        cancelRetryMode();
        setupRetryMode(getSendOnAuthorizeBroadcastDelay());
        prevAuthProblems++;
        BefrestLog.w(TAG, "Authorize Problem Happen");
    }

    private void setupRetryMode(int interval) {
        BefrestLog.v(TAG, "setup retry to run after " + interval);
        messageHandler.postDelayed(retry, interval);
        retryInProgress = true;
    }

    private void cancelRetryMode() {
        BefrestLog.v(TAG, "cancelRetryMode: ");
        messageHandler.removeCallbacks(retry);
        retryInProgress = false;
    }

    private void handleReceivedMessages() {
        BefrestLog.i(TAG, "handleReceivedMessages: ");
        final ArrayList<BefrestMessage> msgs = new ArrayList<>(receivedMessages.size());
        msgs.addAll(receivedMessages);
        receivedMessages.clear();
        Collections.sort(msgs, Util.comparator);
        mainThreadHandler.post(() -> onPushReceived(msgs));
    }

    private int getBatchTime() {
        int requiredTime = TIME_PER_MESSAGE_IN_BATH_MODE * batchSize;
        return (requiredTime < BATCH_MODE_TIMEOUT ? requiredTime : BATCH_MODE_TIMEOUT);
    }

    private void connectIfNetworkAvailable() {
        BefrestLog.w(TAG, "try connect if network available ");
        connectionManager.forward(BefrestEvent.CONNECT);
    }

    private void postFinishBachModeAgain() {
        messageHandler.postDelayed(finishBatchMode, getBatchTime());
    }

    private void refresh() {
        BefrestLog.v(TAG, "refresh requested ");
        if (retryInProgress) {
            cancelRetryMode();
            prevFailedConnectTries = 0;
        }
        connectionManager.forward(BefrestEvent.REFRESH);
    }

    @Override
    public void onDestroy() {
        BefrestLog.w(TAG, "------------------------onDestroy  Start------------------------");
        cancelRetryMode();
        try {
            connectionManager.forward(BefrestEvent.DISCONNECT);
            connectionManager.forward(BefrestEvent.STOP);
            befrestHandler.quit();
            befrestHandler.join();
            befrestHandler = null;
            messageHandlerThread.quit();
            messageHandlerThread.join();
            messageHandlerThread = null;
            Befrest.getInstance().setBefrestStart(false);
            if (Befrest.getInstance().isWantToStart()) {
                if (SDK_INT >= OREO_SDK_INT) {
                    JobServiceManager.getInstance().scheduleJob();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        connectionManager = null;
        BefrestLog.w(TAG, "------------------------onDestroy: Finish------------------------");
        super.onDestroy();
        Befrest.getInstance().setServiceRunning(false);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (SDK_INT < OREO_SDK_INT) {
            BefrestLog.i(TAG, "onTaskRemoved : ");
            befrestContract.setAlarmService(this);
        } else {
            Befrest.getInstance().unRegister();
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onOpen() {
        BefrestLog.i(TAG, "onOpen: socket connection is open");
        prevFailedConnectTries = 0;
        prevAuthProblems = 0;
        onBefrestConnect();
        cancelRetryMode();
    }

    @Override
    public void onClose(int code, String reason) {
        BefrestLog.w(TAG, "onClose: connection close with code :" + code + " and reason : " + reason);
        switch (code) {
            case CLOSE_UNAUTHORIZED:
                handleAuthorizeProblem();
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
                break;
            default:
                scheduleReconnect();
        }
    }

    @Override
    public void onConnectionRefreshed() {
        connectionRefreshed();
    }

    @Override
    public void onBefrestMessage(BefrestMessage msg) {
        BefrestLog.i(TAG, "onBefrestMessage: message received with type : " + msg.getType());
        switch (msg.getType()) {
            case NORMAL:
            case TOPIC:
            case GROUP:
                receivedMessages.add(msg);
                if (!isBachReceiveMode)
                    handleReceivedMessages();
                break;
            case BATCH:
                isBachReceiveMode = true;
                batchSize = Integer.valueOf(msg.getData());
                int batchTime = getBatchTime();
                messageHandler.postDelayed(finishBatchMode, batchTime);
                break;
        }

    }

    @Override
    public void changeConnection(boolean isConnect) {
        BefrestLog.i(TAG, "connection Changed and its state is" + (isConnect ? " connect" : " disconnect"));
        onChangedConnection(isConnect);
    }

    private Runnable retry = () -> Befrest.getInstance().startService(RETRY);
    private Runnable finishBatchMode = () -> {
        int receivedMsgSize = receivedMessages.size();
        if (receivedMsgSize < batchSize - 1) {
            batchSize -= receivedMsgSize;
            postFinishBachModeAgain();
        } else
            isBachReceiveMode = false;

        if (receivedMsgSize > 0)
            handleReceivedMessages();
    };

    /**
     * this method called when new Message(s) received from Server
     *
     * @param messages new Messages that come from Server
     *                 call super() if you want receive message from Receiver(s)
     */
    protected void onPushReceived(ArrayList<BefrestMessage> messages) {
        BefrestLog.i(TAG, "onPushReceived: PushReceived with size" + messages.size());
        Parcelable[] data = new BefrestMessage[messages.size()];
        Bundle b = new Bundle(1);
        b.putParcelableArray(KEY_MESSAGE_PASSED, messages.toArray(data));
        befrestContract.sendBefrestBroadcast(this, PUSH, b);
    }

    /**
     * this method called when Connection has been changed
     *
     * @param isConnect true means connection is live and false means connection is lost
     *                  call super if you want to receive connection change in receiver
     */
    protected void onChangedConnection(boolean isConnect) {
        Bundle b = new Bundle(1);
        b.putBoolean(KEY_MESSAGE_PASSED, isConnect);
        befrestContract.sendBefrestBroadcast(this, BEFREST_CONNECTION_CHANGED, b);
    }

    /**
     * Called when there is a problem with your Authentication token.
     * The Service encounters authorization errors while trying to connect to Befrest servers.
     * The method is called in main thread of the application (UiThread)
     * call super() if you want to receive this callback also in your broadcast receivers.
     */
    protected void onAuthorizeProblem() {
        befrestContract.sendBefrestBroadcast(this, UNAUTHORIZED, null);
    }

    /**
     * is called when Befrest Connects to its server.
     * The method is called in main thread of the application (UiThread)
     * <p>
     * call super() if you want to receive this callback also in your broadcast receivers.
     */

    protected void onBefrestConnect() {
        befrestContract.sendBefrestBroadcast(this, BEFREST_CONNECTED, null);
    }

    protected void connectionRefreshed() {
        befrestContract.sendBefrestBroadcast(this, CONNECTION_REFRESHED, null);
    }
}
