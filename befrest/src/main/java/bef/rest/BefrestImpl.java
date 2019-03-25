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

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static bef.rest.BefrestPrefrences.PREF_AUTH;
import static bef.rest.BefrestPrefrences.PREF_CH_ID;
import static bef.rest.BefrestPrefrences.PREF_CONNECT_ANOMALY_DATA_RECORDING_TIME;
import static bef.rest.BefrestPrefrences.PREF_CONTINUOUS_CLOSES;
import static bef.rest.BefrestPrefrences.PREF_CONTINUOUS_CLOSES_TYPES;
import static bef.rest.BefrestPrefrences.PREF_CUSTOM_PUSH_SERVICE_NAME;
import static bef.rest.BefrestPrefrences.PREF_LAST_SUCCESSFUL_CONNECT_TIME;
import static bef.rest.BefrestPrefrences.PREF_LOG_LEVEL;
import static bef.rest.BefrestPrefrences.PREF_SERVICE_CHOOSER;
import static bef.rest.BefrestPrefrences.PREF_TOPICS;
import static bef.rest.BefrestPrefrences.PREF_U_ID;
import static bef.rest.BefrestPrefrences.getPrefs;
import static bef.rest.BefrestPrefrences.saveInt;
import static bef.rest.BefrestPrefrences.saveLong;
import static bef.rest.BefrestPrefrences.saveString;
import static bef.rest.BefrestPrefrences.saveToPrefs;

/**
 * Main class to interact with BefrestImpl service.
 */
final class BefrestImpl implements Befrest, BefrestInternal, BefrestAppDelegate {
    private static String TAG = BefLog.TAG_PREF + "BefrestImpl";

    static final int START_ALARM_CODE = 676428;
    static final int KEEP_PINGING_ALARM_CODE = 676429;
    private JobScheduler jobScheduler;
    private boolean connectionDataChangedSinceLastStart;
    private BefrestAppLifeCycle befrestAppLifeCycle;
    private Handler handler = new Handler();
    private Runnable startService = new Runnable() {
        @Override
        public void run() {
            if (!isJobScheduled && !isBefrestStarted)
                innerStart();
        }
    };

    BefrestImpl(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = getPrefs(context);
        uId = prefs.getLong(PREF_U_ID, -1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        }
        chId = prefs.getString(PREF_CH_ID, null);
        auth = prefs.getString(PREF_AUTH, null);
        topics = prefs.getString(PREF_TOPICS, "");
        logLevel = prefs.getInt(PREF_LOG_LEVEL, LOG_LEVEL_DEFAULT);
        connectAnomalyDataRecordingStartTime = prefs.getLong(PREF_CONNECT_ANOMALY_DATA_RECORDING_TIME, System.currentTimeMillis());
        loadPushServiceData(prefs);
    }

    private void loadPushServiceData(SharedPreferences prefs) {
        String customPushServicName = prefs.getString(PREF_CUSTOM_PUSH_SERVICE_NAME, null);
        if (customPushServicName == null) {
            pushService = PushService.class;
        } else
            try {
                pushService = Class.forName(customPushServicName);
            } catch (ClassNotFoundException e) {
                BefLog.e(TAG, e);
            }
    }

    Context context;
    Class<?> pushService;

    private long uId;
    private String chId;
    private String auth;
    private int logLevel;
    static boolean isBefrestStarted;
    private boolean isJobScheduled;
    private String topics;


    boolean refreshIsRequested = false;
    private long lastAcceptedRefreshRequestTime = 0;

    private long connectAnomalyDataRecordingStartTime;

    private static final int[] AuthProblemBroadcastDelay = {0, 60 * 1000, 240 * 1000, 600 * 1000};
    int prevAuthProblems = 0;

    private static final long WAIT_TIME_BEFORE_SENDING_CONNECT_ANOMLY_REPORT = 72 * 60 * 60 * 1000; // 72h

    private int reportedContinuousCloses;
    private String continuousClosesTypes;

    private String subscribeUrl;
    private List<NameValuePair> subscribeHeaders;
    private NameValuePair authHeader;

    /**
     * Initialize push receive service. You can also use setter messages for initializing.
     *
     * @param uId  uId
     * @param auth Authentication token
     * @param chId chId
     */
    public Befrest init(long uId, String auth, String chId) {
        if (chId == null || !(chId.length() > 0))
            throw new BefrestException("invalid chId!");
        if (uId != this.uId || (auth != null && !auth.equals(this.auth)) || !chId.equals(this.chId)) {
            this.uId = uId;
            this.auth = auth;
            this.chId = chId;
            clearTempData();
            saveToPrefs(context, uId, auth, chId);
        }
        return this;
    }

    /**
     * @param customPushService that befrest will start in background. This class must extend rest.bef.PushService
     */
    public Befrest setCustomPushService(Class<? extends PushService> customPushService) {
        if (customPushService == null)
            throw new BefrestException("invalid custom push service!");
        else if (isBefrestStarted && !customPushService.equals(pushService)) {
            throw new BefrestException("can not set custom push service after starting befrest!");
        } else {
            this.pushService = customPushService;
            saveString(context, PREF_CUSTOM_PUSH_SERVICE_NAME, customPushService.getName());
        }
        return this;
    }

    /**
     * set uId.
     *
     * @param uId uId
     */
    public Befrest setUId(long uId) {
        if (uId != this.uId) {
            this.uId = uId;
            clearTempData();
            saveLong(context, PREF_U_ID, uId);
        }
        return this;
    }

    /**
     * set chId.
     *
     * @param chId chId
     */
    public Befrest setChId(String chId) {
        if (chId == null || !(chId.length() > 0))
            throw new BefrestException("invalid chId!");
        if (!chId.equals(this.chId)) {
            this.chId = chId;
            clearTempData();
            saveString(context, PREF_CH_ID, chId);
        }
        return this;
    }

    /**
     * set Authentication token.
     *
     * @param auth Authentication Token
     */
    public Befrest setAuth(String auth) {
        if (auth == null || !(auth.length() > 0))
            throw new BefrestException("invalid auth!");
        if (!auth.equals(this.auth)) {
            this.auth = auth;
            clearTempData();
            saveString(context, PREF_AUTH, auth);
        }
        return this;
    }

    /**
     * Start push service. You should set uId and chId before calling this start.
     * Best Practice: call this method in your onCreate() method of your Application class
     * Yous should also call this method anytime you set authToken.
     *
     * @throws IllegalStateException if be called without a prior call to init()
     */

    private void innerStart() {
        BefLog.i(TAG, "starting befrest");
        if (uId < 0 || chId == null || chId.length() < 1)
            throw new BefrestException("uId and chId are not properly defined!");
        saveString(context, PREF_SERVICE_CHOOSER, null);
        if (connectionDataChangedSinceLastStart)
            context.stopService(new Intent(context, pushService));
        BefrestImpl.startService(pushService, context, PushService.CONNECT);
        connectionDataChangedSinceLastStart = false;
        isBefrestStarted = true;
        Util.enableConnectivityChangeListener(context);

    }

    @Override
    public void start() {
        Log.i(TAG, "start: ");
        handler.postDelayed(startService, 500);
        befrestAppLifeCycle = new BefrestAppLifeCycle(this);
        context.registerComponentCallbacks(befrestAppLifeCycle);
        ((Application) context).registerActivityLifecycleCallbacks(befrestAppLifeCycle);
    }

    static void startService(Class<?> pushService, Context context, String flag) {
        try {
            Intent intent = new Intent(context, pushService);
            intent.putExtra(flag, true);
            context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop push service.
     * You can call start to run the service later.
     */
    public void stop() {
        ((Application) context).unregisterActivityLifecycleCallbacks(befrestAppLifeCycle);
        stopBefrest();
        BefLog.i(TAG, "BefrestImpl Service Stopped.");
        BefrestPrefrences.removePrefs(context);
    }

    public Befrest addTopic(String topicName) {
        BefLog.i(TAG, "addTopic: " + topicName);
        if (topicName == null || topicName.length() < 1 || !topicName.matches("[A-Za-z0-9]+"))
            throw new BefrestException("topic name should be an alpha-numeric string!");
        for (String s : topics.split("-"))
            if (s.equals(topicName))
                return this;
        if (topics.length() > 0)
            topics += "-";
        topics += topicName;

        updateTpics(topics);
        BefLog.i(TAG, "Topics: " + topics);
        return this;
    }

    public Befrest addTopics(String... topicsToAdd) {
        if (topicsToAdd == null || topicsToAdd.length < 1)
            return this;
        List<String> currTopics = new ArrayList<>(Arrays.asList(topics.split("-")));
        for (String topic : topicsToAdd) {
            if (topic == null || topic.length() < 1 || !topic.matches("[A-Za-z0-9]+")) {
                BefLog.w(TAG, "invalid topic name : '" + topic + "' (topic name should be an alpha-numeric string!)");
                continue;
            }
            if (currTopics.contains(topic)) {
                BefLog.w(TAG, "topic already exists : '" + topic + "'");
                continue;
            }
            if (topics.length() > 0)
                topics += "-";
            topics += topic;
            currTopics.add(topic);
        }

        updateTpics(this.topics);
        BefLog.i(TAG, "Topics: " + topics);
        return this;
    }

    /**
     * remove a topic from current topics that user has.
     *
     * @param topicName Name of topic to be removed
     */
    public boolean removeTopic(String topicName) {
        String[] splittedTopics = topics.split("-");
        boolean found = false;
        String resTopics = "";
        for (String splittedTopic : splittedTopics) {
            if (splittedTopic.equals(topicName))
                found = true;
            else resTopics += splittedTopic + "-";
        }
        if (!found)
            return false;
        if (resTopics.length() > 0)
            resTopics = resTopics.substring(0, resTopics.length() - 1);
        updateTpics(resTopics);
        BefLog.i(TAG, "Topics: " + topics);
        return true;
    }

    public Befrest removeTopics(String... topicsToRemove) {
        final List<String> toRemove = Arrays.asList(topicsToRemove);
        final List<String> currTopics = Arrays.asList(topics.split("-"));
        String resTopics = "";
        for (String topic : currTopics) {
            if (!toRemove.contains(topic))
                resTopics += topic + "-";
        }
        if (resTopics.length() > 0)
            resTopics = resTopics.substring(0, resTopics.length() - 1);
        updateTpics(resTopics);
        BefLog.i(TAG, "Topics: " + topics);
        return this;
    }

    /**
     * get List of currently added topics
     *
     * @return Topics List
     */
    public String[] getCurrentTopics() {
        if (topics.length() > 0)
            return topics.split("-");
        return new String[0];
    }

    /**
     * Request the push service to refresh its connection. You will be notified through your receivers
     * whenever the connection refreshed.
     *
     * @return true if a request was accepted, false otherwise.
     */
    public boolean refresh() {
        if (!Util.isConnectedToInternet(context) || !isBefrestStarted)
            return false;
        BefLog.i(TAG, "BefrestImpl Is Refreshing ...");
        if (refreshIsRequested && (System.currentTimeMillis() - lastAcceptedRefreshRequestTime) < 10 * 1000)
            return true;
        refreshIsRequested = true;
        lastAcceptedRefreshRequestTime = System.currentTimeMillis();
        BefrestImpl.startService(pushService, context, PushService.REFRESH);
        return true;
    }

    /**
     * Register a new push receiver. Any registered receiver <i><beforeShowCallBack>must be</beforeShowCallBack></i> unregistered
     * by passing the same receiver object to {@link #unregisterPushReceiver}. Actually the method
     * registers a BroadcastReceiver considering security using permissions.
     *
     * @param receiver the receiver object that will receive events
     */
    public void registerPushReceiver(BefrestPushReceiver receiver) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BefrestPushReceiver.ACTION_BEFREST_PUSH);
        context.registerReceiver(receiver, intentFilter, Util.getBroadcastSendingPermission(context), null);
    }

    /**
     * Unregister a previously registered push receiver.
     *
     * @param receiver receiver object to be unregistered
     */
    public void unregisterPushReceiver(BefrestPushReceiver receiver) {
        context.unregisterReceiver(receiver);
    }

    public Befrest setLogLevel(int logLevel) {
        if (logLevel < 0) BefLog.i(TAG, "Invalid Log Level!");
        else {
            saveInt(context, PREF_LOG_LEVEL, logLevel);
            this.logLevel = logLevel;
        }
        return this;
    }

    public int getLogLevel() {
        return logLevel;
    }


    public int getSdkVersion() {
        return Util.SDK_VERSION;
    }


    private void updateTpics(String topics) {
        this.topics = topics;
        saveString(context, PREF_TOPICS, topics);
        clearTempData();
    }


    public void setStartServiceAlarm() {
        Log.i(TAG, "setStartServiceAlarm: ");
        stopBefrest();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        } else {
            AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, pushService).putExtra(PushService.SERVICE_STOPPED, true);
            PendingIntent pi = PendingIntent.getService(context, START_ALARM_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + PushService.START_SERVICE_AFTER_ILLEGAL_STOP_DELAY,
                    3 * 60 * 1000, pi);
            /*
            long triggerAtMillis = SystemClock.elapsedRealtime() + PushService.START_SERVICE_AFTER_ILLEGAL_STOP_DELAY;
            alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pi);
            */
            BefLog.i(TAG, "BefrestImpl Scheduled To Start Service In " + PushService.START_SERVICE_AFTER_ILLEGAL_STOP_DELAY + "ms");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setupJobScheduler() {
        isJobScheduled = true;
        saveString(context, PREF_SERVICE_CHOOSER, "true");
        JobInfo jobInfo = new JobInfo.Builder(1001,
                new ComponentName(context, BackgroundService.class))
                .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(TimeUnit.MINUTES.toMillis(1),
                        JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "setupJobScheduler: ok");
        }

    }

    private void clearTempData() {
        subscribeUrl = null;
        subscribeHeaders = null;
        authHeader = null;
        connectionDataChangedSinceLastStart = true;

    }

    public String getSubscribeUri() {
        if (subscribeUrl == null)
            subscribeUrl = String.format(Locale.US, "wss://gw.bef.rest/xapi/%d/subscribe/%d/%s/%d",
                    Util.API_VERSION, uId, chId, Util.SDK_VERSION);
        return subscribeUrl;
    }


    public List<NameValuePair> getSubscribeHeaders() {
        if (subscribeHeaders == null) {
            subscribeHeaders = new ArrayList<>();
            subscribeHeaders.add(getAuthHeader());

            if (topics != null && topics.length() > 0)
                subscribeHeaders.add(new NameValuePair("X-BF-TOPICS", topics));
        }
        return subscribeHeaders;
    }

    public NameValuePair getAuthHeader() {
        if (authHeader == null) {
            authHeader = new NameValuePair("X-BF-AUTH", auth);
        }
        BefLog.v(TAG, "AuthToken: " + auth);
        return authHeader;
    }

    public int getSendOnAuthorizeBroadcastDelay() {
        int index = prevAuthProblems < AuthProblemBroadcastDelay.length
                ? prevAuthProblems
                : AuthProblemBroadcastDelay.length - 1;
        return AuthProblemBroadcastDelay[index];
    }

    public void sendBefrestBroadcast(Context context, int type, Bundle extras) {
        try {
            Intent intent = new Intent(BefrestPushReceiver.ACTION_BEFREST_PUSH);
            intent.putExtra(BefrestPushReceiver.BROADCAST_TYPE, type);
            if (extras != null) intent.putExtras(extras);
            String permission = BefrestImpl.Util.getBroadcastSendingPermission(context);
            long now = System.currentTimeMillis();
            intent.putExtra(BefrestPushReceiver.KEY_TIME_SENT, "" + now);
            context.getApplicationContext().sendBroadcast(intent, permission);
            BefLog.w(TAG, "broadcast sent::    type: " + type + "      permission:" + permission);
        } catch (Exception ignored) {
            //catch System failure
            BefLog.w(TAG, "could not send broadcast type: " + type);
        }
    }

    public void reportOnClose(Context context, int code) {
        reportedContinuousCloses++;
        BefLog.d(TAG, "reportOnClose :: total:" + reportedContinuousCloses + " code:" + code);
        continuousClosesTypes += code + ",";
        saveString(context, PREF_CONTINUOUS_CLOSES_TYPES, continuousClosesTypes);
        saveInt(context, PREF_CONTINUOUS_CLOSES, reportedContinuousCloses);
        if (System.currentTimeMillis() - connectAnomalyDataRecordingStartTime > WAIT_TIME_BEFORE_SENDING_CONNECT_ANOMLY_REPORT)
            if (reportedContinuousCloses > 75) {

                clearAnomalyHistory();
            }
    }

    public void reportOnOpen(Context context) {
        saveLong(context, PREF_LAST_SUCCESSFUL_CONNECT_TIME, System.currentTimeMillis());
        clearAnomalyHistory();
    }

    private void clearAnomalyHistory() {
        connectAnomalyDataRecordingStartTime = System.currentTimeMillis();
        saveLong(context, PREF_CONNECT_ANOMALY_DATA_RECORDING_TIME, connectAnomalyDataRecordingStartTime);
        reportedContinuousCloses = 0;
        continuousClosesTypes = "";
        saveString(context, PREF_CONTINUOUS_CLOSES_TYPES, continuousClosesTypes);
        saveInt(context, PREF_CONTINUOUS_CLOSES, reportedContinuousCloses);
    }

    private void stopBefrest() {
        Log.i(TAG, "stopBefrest: ");
        if (isBefrestStarted) {
            isBefrestStarted = false;
            context.stopService(new Intent(context, pushService));
            Util.disableConnectivityChangeListener(context);
        }
    }

    @Override
    public void onAppForeGrounded() {
        handler.removeCallbacks(startService);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (jobScheduler != null && jobScheduler.getAllPendingJobs().size() > 0) {
                BefLog.w(TAG, String.valueOf(jobScheduler.getAllPendingJobs().size()));
                jobScheduler.cancelAll();
                isJobScheduled = false;
            }
        }
        Log.i(TAG, "onAppForeGrounded: ");
        innerStart();
    }

    @Override
    public void onAppBackground() {
        handler.removeCallbacks(startService);
        Log.i(TAG, "onAppBackground: ");
        if (Build.VERSION.SDK_INT >= 26) {
            Log.i(TAG, "Api Version is upper than 26");
            stopBefrest();
            setupJobScheduler();
        }
    }

    class BefrestException extends RuntimeException {
        public BefrestException() {
        }

        BefrestException(String detailMessage) {
            super(detailMessage);
        }

        public BefrestException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public BefrestException(Throwable throwable) {
            super(throwable);
        }
    }
}