package bef.rest.befrest.befrest;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import bef.rest.befrest.BackgroundService;
import bef.rest.befrest.PushService;
import bef.rest.befrest.clientData.ClientDataManager;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.Util;
import static bef.rest.befrest.constant.SDKConst.CONNECT;
import static bef.rest.befrest.constant.SDKConst.REFRESH;
import static bef.rest.befrest.utils.BefrestPreferences.setRunnigService;

@SuppressWarnings("UnusedReturnValue")
public class Befrest implements BefrestAppDelegate {
    private static String TAG = Befrest.class.getName();
    @SuppressLint("StaticFieldLeak")
    private static Befrest instance;
    private BefrestAppLifeCycle befrestAppLifeCycle;
    private Context context;
    private Class<?> pushService;
    private boolean isBefrestStart;
    private JobScheduler jobScheduler;
    private boolean isAppInForeground;
    private boolean wantToStart;

    public static Befrest getInstance(Context context) {
        if (instance == null) {
            instance = new Befrest(context);
        }
        return instance;
    }

    private Befrest(Context context) {
        this.context = context;
        ClientDataManager.getInstance(context);
        pushService = PushService.class;
    }

    public Befrest init(int uid, String authToken, String chId) {
        befrestAppLifeCycle = new BefrestAppLifeCycle(this);
        context.registerComponentCallbacks(befrestAppLifeCycle);
        ClientDataManager.getInstance(context).setUId(uid);
        ClientDataManager.getInstance(context).setChId(chId);
        ClientDataManager.getInstance(context).setAuthToken(authToken);
        return this;
    }

    public Befrest start() {
        BefrestLog.i(TAG, "befrest start");
        ((Application) context).registerActivityLifecycleCallbacks(befrestAppLifeCycle);
        wantToStart = true;
            startBefrest();
        return this;
    }

    /**
     * @param topic topic that user want to subscribe on
     * @return befrest instance
     */
    public Befrest addTopic(String topic) {
        if (topic == null || topic.length() < 1 || !topic.matches("[A-Za-z0-9]+")) {
            BefrestLog.w(TAG, "Topic Name ShouldBe AlphaNumeric");
            return this;
        }
        String t = ClientDataManager.getInstance(context).getTopics();
        List<String> currentTopics = Arrays.asList(t.split("-"));
        if (currentTopics.contains(topic)) {
            BefrestLog.w(TAG, "Topic :" + topic + " has Already Exist");
            return this;
        }
        ClientDataManager.getInstance(context).setTopics(topic);

        return this;
    }

    /**
     * @param topicToAdd topics that user want to subscribe on
     * @return befrest instance
     */
    public Befrest addTopics(String... topicToAdd) {
        String t = ClientDataManager.getInstance(context).getTopics();
        List<String> currentTopics = Arrays.asList(t.split("-"));
        for (String s : topicToAdd) {
            if (currentTopics.contains(s)) {
                BefrestLog.w(TAG, "Topic : " + s + " has already exist");
                return this;
            }
            ClientDataManager.getInstance(context).setTopics(s);
        }
        return this;
    }

    public boolean removeTopic(String topicName) {
        String[] splittedTopics = ClientDataManager.getInstance(context).getTopics().split("-");
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
        ClientDataManager.getInstance(context).updateTopic(resTopics.toString());
        return true;
    }

    public Befrest removeTopics(String... topicsToRemove) {
        final List<String> toRemove = Arrays.asList(topicsToRemove);
        String topics = ClientDataManager.getInstance(context).getTopics();
        final String[] currTopics = topics.split("-");
        StringBuilder resTopics = new StringBuilder();
        for (String topic : currTopics) {
            if (!toRemove.contains(topic))
                resTopics.append(topic).append("-");
        }
        if (resTopics.length() > 0)
            resTopics = new StringBuilder(resTopics.substring(0, resTopics.length() - 1));
        BefrestLog.i(TAG, "Topics to remove is " + topics);
        ClientDataManager.getInstance(context).updateTopic(resTopics.toString());
        return this;
    }

    public void refresh() {
        startService(REFRESH);
    }

    /**
     * stop Befrest Service
     */
    public void stop() {
        if (isMyServiceRunning(pushService)) {
            wantToStart = false;
            ((Application)context).unregisterActivityLifecycleCallbacks(befrestAppLifeCycle);
            stopBefrest();
        }
    }

    private void startBefrest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (jobScheduler != null && jobScheduler.getPendingJob(12698) != null) {
                BefrestLog.w(TAG, "job with Id : " + 12698 + " cancel");
                jobScheduler.cancel(12698);
            }
        }
        if (!isMyServiceRunning(pushService)) {
            BefrestLog.i(TAG, "Push Service will be Start");
            startService(CONNECT);
            isBefrestStart = true;
        } else {
            BefrestLog.i(TAG, "startBefrest: PushService is Already Running");
        }
        Util.enableConnectivityChangeListener(context.getApplicationContext());
    }

    public void startService(String event) {
        try {
            BefrestLog.i(TAG, "startService: app in  " + (isAppInForeground ? "foreground" : "background"));
            setRunnigService(context, true);
            Intent intent = new Intent(context, pushService);
            intent.putExtra(event, true);
            context.startService(intent);
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
    public void onAppForeGrounded() {
        BefrestLog.i(TAG, "Application is in foreGround");
        isAppInForeground = true;
        if (wantToStart) {
            startBefrest();
        }
    }

    @Override
    public void onAppBackground() {
        isAppInForeground = false;
        BefrestLog.i(TAG, "application is inBackground");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopBefrest();
            setupJobScheduler();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupJobScheduler() {
        BefrestLog.i(TAG, "setupJobScheduler: ");
        JobInfo jobInfo = new JobInfo.Builder(12698,
                new ComponentName(context, BackgroundService.class))
                .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(TimeUnit.MINUTES.toMillis(1),
                        JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        if (jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS)
            BefrestLog.w(TAG, "job with Id : " + 12698 + " successfully scheduled");

    }

    private void stopBefrest() {
        BefrestLog.i(TAG, "Befrest stopped");
        isBefrestStart = false;
        Intent intent = new Intent(context, pushService);
        context.stopService(intent);
    }

    public void setBefrestStart(boolean befrestStart) {
        isBefrestStart = befrestStart;
    }

    public Context getContext() {
        return context;
    }

    public Class<?> getPushService() {
        return pushService;
    }

    public boolean isBefrestStart() {
        return isBefrestStart;
    }
}
