package bef.rest;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static bef.rest.PushService.TIME_PER_MESSAGE_IN_BATH_MODE;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BackgroundService extends JobService {

    public static final String TAG = BefLog.TAG_PREF + "  BackgroundService ";
    private Handler mainThreadHandler = new Handler();
    private JobParameters parameters;
    private List<BefrestMessage> receivedMessages = new ArrayList<>();
    private BefrestInternal befrestProxy;
    private BefrestImpl befrestActual;
    private static int batchSize;
    private boolean isBachReceiveMode;
    private static final int BATCH_MODE_TIMEOUT = 3000;


    private Handler handler;
    private BefrestConnection mConnection;
    private HandlerThread befrestHandlerThread;

    private WebSocketConnectionHandler wscHandler;
    private Runnable finishJobSuccessfully = new Runnable() {
        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            Log.d(TAG, "finishJobSuccessfully");
            jobFinished(parameters, true);
        }
    };


    Runnable befrestConnected = new Runnable() {
        @Override
        public void run() {
            onBefrestConnected();
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


    @SuppressLint("LongLogTag")
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob: ");
        parameters = params;

        initField();
        connectIfNetworkAvailable();
        mainThreadHandler.postDelayed(finishJobSuccessfully, 15_000);
        return true;
    }

    private void connectIfNetworkAvailable() {
        if (BefrestImpl.Util.isConnectedToInternet(this))
            mConnection.forward(new BefrestEvent(BefrestEvent.Type.CONNECT));
    }

    @SuppressLint("LongLogTag")
    private void initField() {
        Log.d(TAG, "PushService: " + System.identityHashCode(this) + "  onCreate()");
        befrestProxy = BefrestFactory.getInternalInstance(this);
        befrestActual = ((BefrestInvocHandler) Proxy.getInvocationHandler(befrestProxy)).obj;
        createWebsocketConnectionHanlder();
        befrestHandlerThread = new HandlerThread("BefrestThread");
        befrestHandlerThread.start();
        mConnection = new BefrestConnection(this, befrestHandlerThread.getLooper(), wscHandler, befrestProxy.getSubscribeUri(), befrestProxy.getSubscribeHeaders());
        handler = new Handler(befrestHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    super.handleMessage(msg);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
    }

    private void createWebsocketConnectionHanlder() {
        wscHandler = new WebSocketConnectionHandler() {

            @SuppressLint("LongLogTag")
            @Override
            public void onOpen() {
                Log.d(TAG, "Befrest Connected");
                befrestProxy.reportOnOpen(BackgroundService.this);
                befrestActual.prevAuthProblems = 0;
                mainThreadHandler.post(befrestConnected);

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

            }

            @SuppressLint("LongLogTag")
            @Override
            public void onClose(int code, String reason) {
                Log.d(TAG, "WebsocketConnectionHandler: " + System.identityHashCode(this) + "Connection lost. Code: " + code + ", Reason: " + reason);
                Log.d(TAG, "Befrest Connection Closed. Will Try To Reconnect If Possible.");
                befrestProxy.reportOnClose(BackgroundService.this, code);
                switch (code) {
                    case CLOSE_UNAUTHORIZED:
                    case CLOSE_CANNOT_CONNECT:
                    case CLOSE_CONNECTION_LOST:
                    case CLOSE_INTERNAL_ERROR:
                    case CLOSE_NORMAL:
                    case CLOSE_CONNECTION_NOT_RESPONDING:
                    case CLOSE_PROTOCOL_ERROR:
                    case CLOSE_SERVER_ERROR:
                    case CLOSE_HANDSHAKE_TIME_OUT:
                        break;
                }
            }
        };
    }

    private int getBatchTime() {
        int requiredTime = TIME_PER_MESSAGE_IN_BATH_MODE * batchSize;
        return (requiredTime < BATCH_MODE_TIMEOUT ? requiredTime : BATCH_MODE_TIMEOUT);
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ");

        Log.d(TAG, "PushService: " + System.identityHashCode(this) + "==================onDestroy()_START===============");
        if (mConnection != null) {
            mConnection.forward(new BefrestEvent(BefrestEvent.Type.DISCONNECT));
            mConnection.forward(new BefrestEvent(BefrestEvent.Type.STOP));
        }

        try {
            if (befrestHandlerThread != null)
                befrestHandlerThread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mConnection = null;
        befrestHandlerThread = null;
        Log.d(TAG, "PushService==================onDestroy()_END====================");
        super.onDestroy();
    }

    @SuppressLint("LongLogTag")
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.w(TAG, "onStopJob: ");
        mainThreadHandler.removeCallbacks(finishJobSuccessfully);
        return true;
    }

    Comparator<BefrestMessage> comparator = new Comparator<BefrestMessage>() {
        @Override
        public int compare(BefrestMessage lhs, BefrestMessage rhs) {
            return lhs.timeStamp.compareTo(rhs.timeStamp);
        }
    };

    private void handleReceivedMessages() {
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
     * <p>
     * call super() if you want to receive this callback also in your broadcast receivers.
     *
     * @param messages messages
     */
    protected void onPushReceived(ArrayList<BefrestMessage> messages) {
        Parcelable[] data = new BefrestMessage[messages.size()];
        Bundle b = new Bundle(1);
        b.putParcelableArray(BefrestImpl.Util.KEY_MESSAGE_PASSED, messages.toArray(data));
        befrestProxy.sendBefrestBroadcast(this, BefrestPushReceiver.PUSH, b);
    }

    /**
     * is called when Befrest Connects to its server.
     * The method is called in main thread of the application (UiThread)
     * <p>
     * call super() if you want to receive this callback also in your broadcast receivers.
     */
    protected void onBefrestConnected() {
        befrestProxy.sendBefrestBroadcast(this, BefrestPushReceiver.BEFREST_CONNECTED, null);
    }

}
