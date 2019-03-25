package bef.rest;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private Runnable jobFinishSuccessfully = new Runnable() {
        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            saveIntoFile("jobFinishSuccessfully");
            Log.d(TAG, "jobFinishSuccessfully");
            jobFinished(parameters, false);
            BefrestImpl.isBefrestStarted = false;
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
        saveIntoFile("onStartJob");
        Log.d(TAG, "onStartJob: ");

        parameters = params;
        initField();
        connectIfNetworkAvailable();
        BefrestImpl.isBefrestStarted = true;
        mainThreadHandler.postDelayed(jobFinishSuccessfully, 20_000);
        return true;
    }

    private void saveIntoFile(String method) {
        FileOutputStream outputStream = null;
        try {
            outputStream = openFileOutput("savedData.txt", Context.MODE_APPEND);
            Calendar calendar = Calendar.getInstance(Locale.getDefault());
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            int date = calendar.get(Calendar.DAY_OF_MONTH);
            outputStream.write(String.valueOf(method + "-->" + hour + ":" + minute + "\t" + date + "\n\n").getBytes());
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        createWebsocketConnectionHandler();
        befrestHandlerThread = new HandlerThread("BefrestThread");
        befrestHandlerThread.start();
        mConnection = new BefrestConnection(this, befrestHandlerThread.getLooper(), wscHandler, befrestProxy.getSubscribeUri(), befrestProxy.getSubscribeHeaders());
        handler = new Handler(befrestHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    super.handleMessage(msg);
                } catch (Throwable t) {

//                    BefrestImpl.sendCrash(t.getCause().getMessage(), getApplicationContext());
                    throw t;

                }
            }
        };
    }

    private void createWebsocketConnectionHandler() {
        wscHandler = new WebSocketConnectionHandler() {

            @SuppressLint("LongLogTag")
            @Override
            public void onOpen() {
                Log.d(TAG, "Befrest Connected");
                befrestProxy.reportOnOpen(BackgroundService.this);
                befrestActual.prevAuthProblems = 0;
                mainThreadHandler.post(befrestConnected);

            }

            @SuppressLint("LongLogTag")
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
                        break;
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
        saveIntoFile("noDestroy");
        BefLog.i(TAG, "PushService: " + System.identityHashCode(this) + "==================onDestroy()_START===============");
        if (mConnection != null) {
            mConnection.forward(new BefrestEvent(BefrestEvent.Type.DISCONNECT));
            mConnection.forward(new BefrestEvent(BefrestEvent.Type.STOP));
        }

        try {
            if (befrestHandlerThread != null)
                befrestHandlerThread.join(1000);
        } catch (InterruptedException e) {
//            BefrestImpl.sendCrash(e.getCause().getMessage());
//            BefrestImpl.sendCrash(e.getCause().getMessage(), getApplicationContext());
            e.printStackTrace();
        }

        mConnection = null;
        befrestHandlerThread = null;
        BefLog.i(TAG, "PushService==================onDestroy()_END==============================");
        super.onDestroy();
    }

    @SuppressLint("LongLogTag")
    @Override
    public boolean onStopJob(JobParameters params) {
        BefLog.w(TAG, "onStopJob: ");
        saveIntoFile("onStartJob");
        mainThreadHandler.removeCallbacks(jobFinishSuccessfully);
        return false;
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
