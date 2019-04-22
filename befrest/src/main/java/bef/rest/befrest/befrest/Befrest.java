package bef.rest.befrest.befrest;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;

import java.util.Arrays;
import java.util.List;

import bef.rest.befrest.ApplicationStateThread;
import bef.rest.befrest.PushService;
import bef.rest.befrest.clientData.ClientData;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.JobServiceManager;
import bef.rest.befrest.utils.Util;

import static bef.rest.befrest.utils.BefrestPreferences.PREF_CUSTOM_PUSH_SERVICE_NAME;
import static bef.rest.befrest.utils.BefrestPreferences.saveString;
import static bef.rest.befrest.utils.BefrestPreferences.setRunningService;
import static bef.rest.befrest.utils.SDKConst.CONNECT;
import static bef.rest.befrest.utils.SDKConst.OREO_SDK_INT;
import static bef.rest.befrest.utils.SDKConst.REFRESH;
import static bef.rest.befrest.utils.SDKConst.SDK_INT;
import static bef.rest.befrest.utils.Util.isAppOnForeground;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class Befrest implements BefrestAppDelegate {
    private static final String TAG = "Befrest";
    private Context context;
    private Class<?> pushService;
    private boolean isBefrestStart;
    private JobScheduler jobScheduler;
    private boolean isAppInForeground;
    private boolean wantToStart;
    private HandlerThread handlerThread;
    private BefrestAppLifeCycle befrestAppLifeCycle;
    private BefrestContract befrestContract;
    private ApplicationStateThread ap;

    private static class Loader {
        @SuppressLint("StaticFieldLeak")
        static volatile Befrest instance = new Befrest();
    }

    public static Befrest getInstance() {
        return Loader.instance;
    }

    private Befrest() {
        pushService = PushService.class;
        befrestContract = new BefrestContract();

    }

    public static void init(Context context, int uid, String authToken, String chId) {
        Befrest.getInstance().setContext(context);
        ClientData.getInstance().setData(uid, chId, authToken);
    }

    public Befrest start() {
        watchAppLifeCycle();
        befrestContract.registerBroadcastReceiver();
        wantToStart = true;
        startBefrest();
        return this;
    }

    private void watchAppLifeCycle() {
        BefrestLog.i(TAG, "init App Life Cycle");
        handlerThread = new HandlerThread("Application State");
        handlerThread.start();
        ap = new ApplicationStateThread(handlerThread.getLooper(), this);
        ap.forward(true);
        befrestAppLifeCycle = new BefrestAppLifeCycle(this);
        context.registerComponentCallbacks(befrestAppLifeCycle);
        ((Application) context).registerActivityLifecycleCallbacks(befrestAppLifeCycle);
    }

    /**
     * @param customPushService that befrest will start This class must extend rest.bef.PushService
     */
    public Befrest setCustomPushService(Class<? extends PushService> customPushService) {
        if (customPushService == null)
            BefrestLog.w(TAG, "custom PushService can not be null");
        else if (isMyServiceRunning(pushService) && !customPushService.equals(pushService)) {
            BefrestLog.w(TAG, "can not assign customPushService after service run");
        } else {
            this.pushService = customPushService;
            saveString(PREF_CUSTOM_PUSH_SERVICE_NAME, customPushService.getName());
        }
        return this;
    }

    /**
     * @param topic topic that user want to subscribe on
     * @return befrest instance
     */
    public Befrest addTopic(String topic) {
        if (topic == null || topic.length() < 1 || !topic.matches("[A-Za-z0-9]+")) {
            BefrestLog.w(TAG, "Topic Name Should be AlphaNumeric");
            return this;
        }
        String t = ClientData.getInstance().getTopics();
        List<String> currentTopics = Arrays.asList(t.split("-"));
        if (currentTopics.contains(topic)) {
            BefrestLog.w(TAG, "Topic :" + topic + " has Already Exist");
            return this;
        }
        ClientData.getInstance().setTopics(topic);
        return this;
    }

    /**
     * @param topicToAdd topics that user want to subscribe on
     * @return befrest instance
     */
    public Befrest addTopics(String... topicToAdd) {
        String t = ClientData.getInstance().getTopics();
        List<String> currentTopics = Arrays.asList(t.split("-"));
        for (String s : topicToAdd) {
            if (currentTopics.contains(s)) {
                BefrestLog.w(TAG, "Topic : " + s + " has already exist");
                return this;
            }
            ClientData.getInstance().setTopics(s);
        }
        return this;
    }

    public boolean removeTopic(String topicName) {
        String[] splittedTopics = ClientData.getInstance().getTopics().split("-");
        boolean found = false;
        StringBuilder resTopics = new StringBuilder();
        for (String splittedTopic : splittedTopics) {
            if (splittedTopic.equals(topicName))
                found = true;
            else resTopics.append(splittedTopic).append("-");
        }
        if (!found)
            return false;
        if (resTopics.length() > 0)
            resTopics = new StringBuilder(resTopics.substring(0, resTopics.length() - 1));
        ClientData.getInstance().updateTopic(resTopics.toString());
        return true;
    }

    public Befrest removeTopics(String... topicsToRemove) {
        final List<String> toRemove = Arrays.asList(topicsToRemove);
        String topics = ClientData.getInstance().getTopics();
        final String[] currTopics = topics.split("-");
        StringBuilder resTopics = new StringBuilder();
        for (String topic : currTopics) {
            if (!toRemove.contains(topic))
                resTopics.append(topic).append("-");
        }
        if (resTopics.length() > 0)
            resTopics = new StringBuilder(resTopics.substring(0, resTopics.length() - 1));
        BefrestLog.i(TAG, "Topics to remove is " + topics);
        ClientData.getInstance().updateTopic(resTopics.toString());
        return this;
    }

    public void refresh() {
        startService(REFRESH);
    }

    /**
     * stop Befrest Service
     */
    public void stop() {
        befrestContract.unRegisterBroadCastReceiver();
        ap.forward(true);
        (context).unregisterComponentCallbacks(befrestAppLifeCycle);
        try {
            handlerThread.join(1000);
            handlerThread.quit();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (isMyServiceRunning(pushService)) {
            wantToStart = false;
            stopBefrest();
        }
    }

    private void startBefrest() {
        if (SDK_INT >= OREO_SDK_INT) {
            JobServiceManager.getInstance().cancelJob();
        }
        if (!isMyServiceRunning(pushService)) {
            BefrestLog.i(TAG, "startBefrest: Push Service will be Start");
            startService(CONNECT);
        } else {
            BefrestLog.i(TAG, "startBefrest: PushService is Already Running");
        }
        Util.enableConnectivityChangeListener(context.getApplicationContext());
    }

    public void startService(String event) {
        try {
            isAppInForeground = isAppOnForeground();
            if (isAppInForeground || SDK_INT < OREO_SDK_INT) {
                BefrestLog.i(TAG, "Start Service : with event " + event);
                setRunningService(true);
                Intent intent = new Intent(context, pushService);
                intent.putExtra(event, true);
                context.startService(intent);
                isBefrestStart = true;
            } else {
                BefrestLog.e(TAG, "Application is in Background and service can't Start");
                stopBefrest();
            }

        } catch (Exception ignored) {

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

    @Override
    public void onAppForeground() {
        BefrestLog.d(TAG, "Application Is In Foreground");
        isAppInForeground = true;
        if (wantToStart)
            startBefrest();
    }

    @Override
    public void onAppBackground() {
        isAppInForeground = false;
        BefrestLog.d(TAG, "Application is in Background");
        if (SDK_INT >= OREO_SDK_INT) {
            stopBefrest();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupJobScheduler() {
        JobServiceManager.getInstance().scheduleJob();
    }

    private void stopBefrest() {
        BefrestLog.v(TAG, "Befrest stopped");
        Intent intent = new Intent(context, pushService);
        context.stopService(intent);
        isBefrestStart = false;
        BefrestLog.d(TAG, "want to Start is " + wantToStart + " Api Level is : " + SDK_INT);
        if (wantToStart && SDK_INT > OREO_SDK_INT)
            setupJobScheduler();
    }

    public void setLogLevel(int logLevel) {
        ClientData.getInstance().setLogLevel(logLevel);
    }


    public void setBefrestStart(boolean befrestStart) {
        isBefrestStart = befrestStart;
    }

    public Context getContext() {
        return context;
    }

    Class<?> getPushService() {
        return pushService;
    }

    public boolean isBefrestStart() {
        return isBefrestStart;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public boolean isAppInForeground() {
        return isAppInForeground;
    }

    public void setAppInForeground(boolean appInForeground) {
        isAppInForeground = appInForeground;
    }
}
