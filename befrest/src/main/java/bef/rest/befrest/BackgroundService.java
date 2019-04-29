package bef.rest.befrest;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bef.rest.befrest.autobahnLibrary.SocketCallBacks;
import bef.rest.befrest.befrest.Befrest;
import bef.rest.befrest.befrest.BefrestConnectionMode;
import bef.rest.befrest.befrest.BefrestContract;
import bef.rest.befrest.befrest.BefrestEvent;
import bef.rest.befrest.befrest.BefrestMessage;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.Util;

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
import static bef.rest.befrest.utils.SDKConst.CONNECTION_REFRESHED;
import static bef.rest.befrest.utils.SDKConst.KEY_MESSAGE_PASSED;
import static bef.rest.befrest.utils.SDKConst.PUSH;
import static bef.rest.befrest.utils.SDKConst.TIME_PER_MESSAGE_IN_BATH_MODE;
import static bef.rest.befrest.utils.SDKConst.UNAUTHORIZED;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BackgroundService extends JobService implements SocketCallBacks {

    private static final String TAG = "BackgroundService";
    private JobParameters parameters;
    private Handler mainThreadHandler;
    private HandlerThread befrestHandler;
    private ConnectionManager connectionManager;
    private Handler messageHandler;
    private List<BefrestMessage> receivedMessages = new ArrayList<>();
    private boolean isBachReceiveMode;
    private int batchSize;

    @Override
    public boolean onStartJob(JobParameters params) {
        BefrestLog.d(TAG, "onStartJob: job started");
        Befrest.getInstance().setServiceRunning(false);
        parameters = params;
        init();
        connectIfNetworkAvailable();
        mainThreadHandler.postDelayed(jobFinishSuccessfully, 20_000);
        return true;
    }

    private void connectIfNetworkAvailable() {
        BefrestLog.i(TAG, "try to connect if network is available");
        if (Util.isConnectedToInternet(this)) {
            connectionManager.forward(BefrestEvent.CONNECT);
            Befrest.getInstance().setBefrestStart(true);
        }
    }

    private void init() {
        mainThreadHandler = new Handler();
        befrestHandler = new HandlerThread("BefrestThread");
        befrestHandler.start();
        connectionManager = new ConnectionManager(befrestHandler.getLooper(), this);
        messageHandler = new Handler(befrestHandler.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    super.handleMessage(msg);
                } catch (Exception e) {
                    //todo handle exception
                }
            }
        };

    }

    @Override
    public boolean onStopJob(JobParameters params) {
        BefrestLog.d(TAG, "onStopJob:  job stopped");
        mainThreadHandler.removeCallbacks(jobFinishSuccessfully);
        return false;
    }

    @Override
    public void onDestroy() {
        if (connectionManager != null) {
            connectionManager.forward(BefrestEvent.DISCONNECT);
            connectionManager.forward(BefrestEvent.STOP);
        }
        try {
            if (befrestHandler != null) {
                befrestHandler.join(1000);
                befrestHandler.quit();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        connectionManager = null;
        befrestHandler = null;
        super.onDestroy();
    }

    @Override
    public void onOpen() {
        BefrestLog.v(TAG, "onOpen: socket is open");
        onBefrestConnect();
    }

    @Override
    public void onClose(int code, String reason) {
        BefrestLog.w(TAG, "onClose: connection close with code :" + code + " and reason : " + reason);
        switch (code) {
            case CLOSE_UNAUTHORIZED:
                onAuthorizeProblem();
                break;
            case CLOSE_CANNOT_CONNECT:
            case CLOSE_CONNECTION_LOST:
            case CLOSE_INTERNAL_ERROR:
            case CLOSE_NORMAL:
            case CLOSE_CONNECTION_NOT_RESPONDING:
            case CLOSE_PROTOCOL_ERROR:
            case CLOSE_SERVER_ERROR:
            case CLOSE_HANDSHAKE_TIME_OUT:
                connectIfNetworkAvailable();
                break;
            default:
                connectIfNetworkAvailable();
        }
    }

    @Override
    public void onConnectionRefreshed() {
        connectionRefreshed();
    }

    @Override
    public void onBefrestMessage(BefrestMessage msg) {
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
    public void onChangeConnection(BefrestConnectionMode befrestConnectionMode, String failureReason) {
        onChangedConnection(befrestConnectionMode);

    }

    @Override
    public void pingServer() {

    }

    private int getBatchTime() {
        int requiredTime = TIME_PER_MESSAGE_IN_BATH_MODE * batchSize;
        return (requiredTime < BATCH_MODE_TIMEOUT ? requiredTime : BATCH_MODE_TIMEOUT);
    }

    private void handleReceivedMessages() {
        final ArrayList<BefrestMessage> msgs = new ArrayList<>(receivedMessages.size());
        msgs.addAll(receivedMessages);
        receivedMessages.clear();
        Collections.sort(msgs, Util.comparator);
        mainThreadHandler.post(() -> onPushReceived(msgs));
    }

    /**
     * this method called when new Message(s) received from Serve <br>
     *
     * @param messages new Messages that come from Server
     *                 call super() if you want receive message from Receiver(s)
     */
    protected void onPushReceived(ArrayList<BefrestMessage> messages) {
        Parcelable[] data = new BefrestMessage[messages.size()];
        Bundle b = new Bundle(1);
        b.putParcelableArray(KEY_MESSAGE_PASSED, messages.toArray(data));
        BefrestContract.getInstance().sendBefrestBroadcast(this, PUSH, b);
    }

    private void postFinishBachModeAgain() {
        messageHandler.postDelayed(finishBatchMode, getBatchTime());
    }

    private Runnable jobFinishSuccessfully = () ->
    {
        BefrestLog.i(TAG, "jobFinishSuccessfully");
        jobFinished(parameters, false);
    };

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
     * this method called when Connection has been changed
     *
     * @param connectionMode has three state :<br>
     *                       CONNECTED mean befrest is connect<br>
     *                       DISCONNECTED mean befrest is disconnect<br>
     *                       RETRY mean befrest try to connect
     */
    protected void onChangedConnection(BefrestConnectionMode connectionMode) {
        Bundle b = new Bundle(1);
        b.putSerializable(KEY_MESSAGE_PASSED, connectionMode);
        BefrestContract.getInstance().sendBefrestBroadcast(this, BEFREST_CONNECTION_CHANGED, b);
    }

    /**
     * Called when there is a problem with your Authentication token.<br>
     * The Service encounters authorization errors while trying to connect to Befrest servers.<br>
     * The method is called in main thread of the application (UiThread)<br>
     * call super() if you want to receive this callback also in your broadcast receivers.
     */
    protected void onAuthorizeProblem() {
        BefrestContract.getInstance().sendBefrestBroadcast(this, UNAUTHORIZED, null);
    }

    /**
     * is called when Befrest Connects to its server.
     * The method is called in main thread of the application (UiThread)
     * <p>
     * call super() if you want to receive this callback also in your broadcast receivers.
     */
    protected void onBefrestConnect() {
        BefrestContract.getInstance().sendBefrestBroadcast(this, BEFREST_CONNECTED, null);
    }

    protected void connectionRefreshed() {
        BefrestContract.getInstance().sendBefrestBroadcast(this, CONNECTION_REFRESHED, null);
    }
}
