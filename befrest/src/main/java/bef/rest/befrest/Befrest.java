package bef.rest.befrest;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.util.Arrays;
import java.util.List;

import bef.rest.befrest.befrest.BefrestAppDelegate;
import bef.rest.befrest.befrest.BefrestAppLifeCycle;
import bef.rest.befrest.befrest.BefrestConnectionMode;
import bef.rest.befrest.befrest.BefrestContract;
import bef.rest.befrest.befrest.BefrestReceiver;
import bef.rest.befrest.clientData.ClientData;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.JobServiceManager;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.utils.WatchSdk;

import static bef.rest.befrest.utils.SDKConst.ACTION_BEFREST_PUSH;
import static bef.rest.befrest.utils.SDKConst.CONNECT;
import static bef.rest.befrest.utils.SDKConst.OREO_SDK_INT;
import static bef.rest.befrest.utils.SDKConst.REFRESH;
import static bef.rest.befrest.utils.SDKConst.SDK_INT;

public class Befrest implements BefrestAppDelegate {
    private static final String TAG = "Befrest";
    private Context context;
    private Class<?> pushService;
    private Class<?> backgroundService;
    private boolean isBefrestStart;
    private boolean wantToStart;
    private BefrestConnectionMode befrestConnectionMode = BefrestConnectionMode.DISCONNECTED;
    private static final int BUILD_NUMBER = 8;

    private BefrestAppLifeCycle befrestAppLifeCycle;
    private boolean isServiceRunning = false;

    private static class Loader {
        @SuppressLint("StaticFieldLeak")
        static volatile Befrest instance = new Befrest();
    }

    public static Befrest getInstance() {
        return Loader.instance;
    }

    private Befrest() {
        pushService = PushService.class;
        backgroundService = BackgroundService.class;
    }

    public static void init(Context context, int uid, String authToken, String chId) {
        if (context == null || chId == null || chId.length() <= 0)
            throw new RuntimeException("invalid chId or context is null");
        Befrest.getInstance().setContext(context.getApplicationContext());
        ClientData.getInstance().setData(uid, chId, authToken);
    }

    public static void init(Context context) {
        if (context == null)
            throw new RuntimeException("context can't be null");
        Befrest.getInstance().setContext(context);
    }

    @SuppressWarnings("WeakerAccess")
    public void setUId(int uid) {
        ClientData.getInstance().setUId(uid);
    }

    @SuppressWarnings("WeakerAccess")
    public void setChId(String chId) {
        if (chId == null || !(chId.length() > 0))
            throw new RuntimeException("invalid chId");
        ClientData.getInstance().setChId(chId);
    }

    @SuppressWarnings("WeakerAccess")
    public void setAuthToken(String authToken) {
        ClientData.getInstance().setAuthToken(authToken);
    }

    public Befrest setClientData(int uId, String chId, String authToken) {
        setUId(uId);
        setChId(chId);
        setAuthToken(authToken);
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Befrest start() {
        BefrestContract.getInstance().registerBroadcastReceiver();
        wantToStart = true;
        startBefrest();
        watchAppLifeCycle();
        return this;
    }

    private void watchAppLifeCycle() {
        befrestAppLifeCycle = new BefrestAppLifeCycle(this);
        context.registerComponentCallbacks(befrestAppLifeCycle);
        try {
            ((Application) context).registerActivityLifecycleCallbacks(befrestAppLifeCycle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param customPushService that befrest will start This class must extend rest.bef.PushService
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public Befrest setCustomPushService(Class<? extends PushService> customPushService) {
        if (customPushService == null) {
            BefrestLog.w(TAG, "custom PushService can't be null");
            throw new RuntimeException("custom pushService can't be null");
        } else if (isMyServiceRunning(pushService) && !customPushService.equals(pushService)) {
            BefrestLog.w(TAG, "can not assign customPushService after service run");
        } else {
            this.pushService = customPushService;
        }
        return this;
    }

    @SuppressWarnings("unused")
    public Befrest setCustomBackgroundService(Class<? extends BackgroundService> customBackgroundService) {
        if (customBackgroundService == null) {
            BefrestLog.w(TAG, "custom background service can't be null");
            throw new RuntimeException("custom background Service can't be null");
        } else if (isMyServiceRunning(pushService) && !customBackgroundService.equals(pushService)) {
            BefrestLog.w(TAG, "can not assign custom background service after service run");
        } else {
            this.backgroundService = customBackgroundService;
        }
        return this;
    }

    /**
     * @param topics that user want to subscribe on
     * @return befrest instance
     */
    @SuppressWarnings("UnusedReturnValue")
    public Befrest addTopics(String... topics) {
        try {
            String t = ClientData.getInstance().getTopics();
            List<String> currentTopics = Arrays.asList(t.split("-"));
            for (String s : topics) {
                if (s.trim().length() == 0 || !s.matches("[A-Za-z0-9]+")) {
                    BefrestLog.w(TAG, "Topic Name Should be AlphaNumeric");
                    continue;
                }
                if (currentTopics.contains(s)) {
                    BefrestLog.w(TAG, "Topic : " + s + " has already exist");
                    continue;
                }
                ClientData.getInstance().addTopic(s);
            }
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
        }
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Befrest removeTopics(String... topics) {
        try {
            final List<String> toRemove = Arrays.asList(topics);
            String currentTopics = ClientData.getInstance().getTopics();
            final String[] currTopics = currentTopics.split("-");
            StringBuilder resTopics = new StringBuilder();
            for (String topic : currTopics) {
                if (!toRemove.contains(topic))
                    resTopics.append(topic).append("-");
            }
            if (resTopics.length() > 0)
                resTopics = new StringBuilder(resTopics.substring(0, resTopics.length() - 1));
            BefrestLog.i(TAG, "Topics to remove is " + currentTopics);
            ClientData.getInstance().updateTopic(resTopics.toString());
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
        }
        return this;
    }

    @SuppressWarnings("unused")
    public void clearTopic() {
        ClientData.getInstance().clearTopic();
    }

    public void refresh() {
        startService(REFRESH);
    }

    /**
     * stop Befrest Service
     */
    public void stop() {
        BefrestContract.getInstance().unRegisterBroadCastReceiver();
        if (isMyServiceRunning(pushService)) {
            wantToStart = false;
            stopBefrest();
        }
    }

    private void startBefrest() {
        if (!isMyServiceRunning(pushService)) {
            BefrestLog.i(TAG, "startBefrest: Service is starting");
            isBefrestStart = true;
            startService(CONNECT);
        } else {
            BefrestLog.i(TAG, "startBefrest: Service is already running");
        }
    }

    void startService(String event) {
        try {
            BefrestLog.i(TAG, "Start Service : with event " + event);
            isServiceRunning = true;
            Intent intent = new Intent(context, pushService);
            intent.putExtra(event, true);
            context.startService(intent);
            isBefrestStart = true;
        } catch (IllegalStateException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WatchSdk.reportCrash(e, null);
                setupJobScheduler();
            }
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupJobScheduler() {
        JobServiceManager.getInstance().scheduleJob();
    }

    private void stopBefrest() {
        unregisterWatchAppLifeCycle();
        BefrestLog.v(TAG, "Befrest stopped");
        Intent intent = new Intent(context, pushService);
        context.stopService(intent);
        BefrestLog.d(TAG, "want to Start is " + wantToStart + " , Api Level is : " + SDK_INT);
    }

    void unregisterWatchAppLifeCycle() {
        try {
            ((Application) context).unregisterActivityLifecycleCallbacks(befrestAppLifeCycle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAppForeground() {
        BefrestLog.i(TAG, "application is in foreground currently");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            JobServiceManager.getInstance().cancelJob();
        }
        if (isBefrestStart && wantToStart) {
            if (SDK_INT >= OREO_SDK_INT) {
                startService(CONNECT);
            }
        }
    }

    @Override
    public void onAppBackground() {
    }

    public void setLogLevel(int logLevel) {
        ClientData.getInstance().setLogLevel(logLevel);
    }

    void setBefrestStart(boolean befrestStart) {
        isBefrestStart = befrestStart;
    }

    public Context getContext() {
        return context;
    }

    public Class<?> getPushService() {
        return pushService;
    }

    boolean isBefrestStart() {
        return isBefrestStart;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public String[] getTopics() {
        try {
            return ClientData.getInstance().getTopics().split("-");
        } catch (Exception e) {
            WatchSdk.reportCrash(e, null);
        }
        return "".split("-");
    }

    @SuppressWarnings("unused")
    public void commitTopics() {
        ClientData.getInstance().clearTopicHeader();
        stop();
        start();
    }

    /**
     * Register a new push receiver. Any registered receiver <i><b>must be</b></i> unregistered
     * by passing the same receiver object to {@link #unregisterPushReceiver}. Actually the method
     * registers a BroadcastReceiver considering security using permissions.
     *
     * @param receiver the receiver object that will receive events
     */
    public void registerPushReceiver(BefrestReceiver receiver) {
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_BEFREST_PUSH);
            context.registerReceiver(receiver, intentFilter, Util.getBroadcastSendingPermission(context), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Unregister a previously registered push receiver.
     *
     * @param receiver receiver object to be unregistered
     */
    public void unregisterPushReceiver(BefrestReceiver receiver) {
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean isWantToStart() {
        return wantToStart;
    }

    boolean isServiceRunning() {
        return isServiceRunning;
    }

    void setServiceRunning(boolean serviceRunning) {
        isServiceRunning = serviceRunning;
    }

    public Class<?> getBackgroundService() {
        return backgroundService;
    }

    public int getBuildNumber() {
        return BUILD_NUMBER;
    }

    public void setBackgroundService(Class<?> backgroundService) {
        this.backgroundService = backgroundService;
    }

    public BefrestConnectionMode getBefrestConnectionMode() {
        return befrestConnectionMode;
    }

    void setBefrestConnectionMode(BefrestConnectionMode befrestConnectionMode) {
        this.befrestConnectionMode = befrestConnectionMode;
    }

    @SuppressWarnings("unused")
    public boolean isBefrestInitialized() {
        return getContext() != null;
    }


}
