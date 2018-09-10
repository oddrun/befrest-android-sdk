package bef.rest;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static bef.rest.ACRAConstants.DATE_TIME_FORMAT_STRING;
import static bef.rest.ACRAConstants.DEFAULT_BUFFER_SIZE_IN_BYTES;
import static bef.rest.ACRAConstants.DEFAULT_REPORT_FIELDS;
import static bef.rest.ACRAConstants.DEFAULT_TAIL_COUNT;
import static bef.rest.ACRAReportField.ANDROID_VERSION;
import static bef.rest.ACRAReportField.APP_VERSION_CODE;
import static bef.rest.ACRAReportField.APP_VERSION_NAME;
import static bef.rest.ACRAReportField.AVAILABLE_MEM_SIZE;
import static bef.rest.ACRAReportField.BRAND;
import static bef.rest.ACRAReportField.BUILD;
import static bef.rest.ACRAReportField.CRASH_CONFIGURATION;
import static bef.rest.ACRAReportField.CUSTOM_DATA;
import static bef.rest.ACRAReportField.DEVICE_FEATURES;
import static bef.rest.ACRAReportField.DEVICE_ID;
import static bef.rest.ACRAReportField.DISPLAY;
import static bef.rest.ACRAReportField.DUMPSYS_MEMINFO;
import static bef.rest.ACRAReportField.ENVIRONMENT;
import static bef.rest.ACRAReportField.EVENTSLOG;
import static bef.rest.ACRAReportField.FILE_PATH;
import static bef.rest.ACRAReportField.INITIAL_CONFIGURATION;
import static bef.rest.ACRAReportField.INSTALLATION_ID;
import static bef.rest.ACRAReportField.IS_SILENT;
import static bef.rest.ACRAReportField.LOGCAT;
import static bef.rest.ACRAReportField.MEDIA_CODEC_LIST;
import static bef.rest.ACRAReportField.PACKAGE_NAME;
import static bef.rest.ACRAReportField.PHONE_MODEL;
import static bef.rest.ACRAReportField.PRODUCT;
import static bef.rest.ACRAReportField.RADIOLOG;
import static bef.rest.ACRAReportField.SETTINGS_GLOBAL;
import static bef.rest.ACRAReportField.SETTINGS_SECURE;
import static bef.rest.ACRAReportField.SETTINGS_SYSTEM;
import static bef.rest.ACRAReportField.SHARED_PREFERENCES;
import static bef.rest.ACRAReportField.STACK_TRACE;
import static bef.rest.ACRAReportField.STACK_TRACE_HASH;
import static bef.rest.ACRAReportField.THREAD_DETAILS;
import static bef.rest.ACRAReportField.TOTAL_MEM_SIZE;
import static bef.rest.ACRAReportField.USER_CRASH_DATE;
import static bef.rest.ACRAReportField.USER_IP;
import static bef.rest.BefrestPrefrences.PREF_CH_ID;
import static bef.rest.BefrestPrefrences.PREF_U_ID;

final class ACRACrashReport {
    private static final String TAG = "ACRACrashReport";

    Context appContext;
    String message;
    Thread uncaughtExceptionThread;
    Throwable exception;

    public ACRACrashReport(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public ACRACrashReport(Context context, Throwable exception) {
        this.exception = exception;
        this.appContext = context;
        this.uncaughtExceptionThread = Thread.currentThread();
    }

    public ACRACrashReport(Context context, String message, Thread uncaughtExceptionThread, Throwable exception) {
        this.appContext = context.getApplicationContext();
        this.message = message;
        this.uncaughtExceptionThread = uncaughtExceptionThread;
        this.exception = exception;
    }

    public ACRACrashReport(Context context, String message) {
        this.appContext = context.getApplicationContext();
        this.message = message;
    }

    private final Map<String, String> customData = new HashMap<String, String>();

    public void setHandled(boolean b) {
        addCustomData("Handled", "" + b);
    }

    /**
     * Sets additional values to be added to {@code CUSTOM_DATA}. Values
     * specified here take precedence over globally specified custom data.
     *
     * @param customData a map of custom key-values to be attached to the handleException
     * @return the updated {@code ACRACrashReport}
     */
    public ACRACrashReport addCustomData(Map<String, String> customData) {
        this.customData.putAll(customData);
        return this;
    }

    /**
     * Sets an additional value to be added to {@code CUSTOM_DATA}. The value
     * specified here takes precedence over globally specified custom data.
     *
     * @param key   the key identifying the custom data
     * @param value the value for the custom data entry
     * @return the updated {@code ACRACrashReport}
     */
    public ACRACrashReport addCustomData(String key, String value) {
        customData.put(key, value);
        return this;
    }

    /**
     * Assembles and sends the crash handleException
     */
    public void report() {
        BefLog.d(TAG, "Generating Crash Report...");
        try {
            addCustomData("ANDROID_SKD_VERSION_NAME", BefLog.SDK_VERSION_NAME);
            addCustomData("SDK_VERSION", "" + BefrestInternal.Util.SDK_VERSION);
            addCustomData("ANDROID_ID", Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID));
        } catch (Throwable t) {
        }
        final ACRACrashReportData crashReportData = createCrashData();
        final File reportFile = getReportFileName(crashReportData);
        saveCrashReportFile(reportFile, crashReportData);
    }

    private File getReportFileName(ACRACrashReportData crashData) {
        final String timestamp = crashData.getProperty(USER_CRASH_DATE);
        final String isSilent = crashData.getProperty(IS_SILENT);
        final String fileName = ""
                + (timestamp != null ? timestamp : new Date().getTime()) // Need to check for null because old version of ACRA did not always capture USER_CRASH_DATE
                + (isSilent != null ? ACRAConstants.SILENT_SUFFIX : "")
                + ACRAConstants.REPORTFILE_EXTENSION;
        final ACRAReportLocator reportLocator = new ACRAReportLocator(appContext);
        return new File(reportLocator.getUnapprovedFolder(), fileName);
    }

    private void saveCrashReportFile(File file, ACRACrashReportData crashData) {
        try {
            final ACRACrashReportPersister persister = new ACRACrashReportPersister();
            persister.store(crashData, file);
        } catch (Exception e) {
            //log
        }
    }

    public ACRACrashReportData createCrashData() {
        final ACRACrashReportData crashReportData = new ACRACrashReportData();
        try {
            final List<ACRAReportField> crashReportFields = Arrays.asList(DEFAULT_REPORT_FIELDS);

            // Make every entry here bullet proof and move any slightly dodgy
            // ones to the end.
            // This ensures that we collect as much info as possible before
            // something crashes the collection process.

            try {
                crashReportData.put(STACK_TRACE, getStackTrace(message, exception));
            } catch (RuntimeException e) {
            }

            // Collect DropBox and logcat. This is done first because some ROMs spam the log with every get on
            // Settings.
            final ACRAPackageManagerWrapper pm = new ACRAPackageManagerWrapper(appContext);

            // Before JellyBean, this required the READ_LOGS permission
            // Since JellyBean, READ_LOGS is not granted to third-party apps anymore for security reasons.
            // Though, we can call logcat without any permission and still get traces related to our app.
            final boolean hasReadLogsPermission = pm.hasPermission(Manifest.permission.READ_LOGS) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);
            if (hasReadLogsPermission) {
                BefLog.d(TAG, "READ_LOGS granted! ACRA can include LogCat and DropBox data.");
                if (crashReportFields.contains(LOGCAT)) {
                    try {
                        crashReportData.put(LOGCAT, collectLogCat(null));
                    } catch (RuntimeException e) {
                        BefLog.e(TAG, "Error while retrieving LOGCAT data", e);
                    }
                }
                if (crashReportFields.contains(EVENTSLOG)) {
                    try {
                        crashReportData.put(EVENTSLOG, collectLogCat("events"));
                    } catch (RuntimeException e) {
                        BefLog.e(TAG, "Error while retrieving EVENTSLOG data", e);
                    }
                }
                if (crashReportFields.contains(RADIOLOG)) {
                    try {
                        crashReportData.put(RADIOLOG, collectLogCat("radio"));
                    } catch (RuntimeException e) {
                        BefLog.e(TAG, "Error while retrieving RADIOLOG data", e);
                    }
                }
            } else {
                BefLog.d(TAG, "READ_LOGS not allowed. ACRA will not include LogCat and DropBox data.");
            }

            try {
                crashReportData.put(ACRAReportField.USER_APP_START_DATE, getTimeString(System.currentTimeMillis()));
                //TODO change current time to time app has started!
            } catch (RuntimeException e) {
                BefLog.e(TAG, "Error while retrieving USER_APP_START_DATE data", e);
            }

            crashReportData.put(IS_SILENT, "true");

            // Always generate handleException uuid
            BefLog.d(TAG, "ACRA adding uuid");
            try {
                crashReportData.put(ACRAReportField.REPORT_ID, UUID.randomUUID().toString());
            } catch (RuntimeException e) {
                BefLog.e(TAG, "Error while retrieving REPORT_ID data", e);
            }

            // Always generate crash time
            GregorianCalendar calendar = new GregorianCalendar();
            try {
                crashReportData.put(ACRAReportField.USER_CRASH_DATE, getTimeString(calendar.getTimeInMillis()));
            } catch (RuntimeException e) {
                BefLog.e(TAG, "Error while retrieving USER_CRASH_DATE data", e);
            }

            // StackTrace hash
            if (crashReportFields.contains(STACK_TRACE_HASH)) {
                try {
                    crashReportData.put(ACRAReportField.STACK_TRACE_HASH, getStackTraceHash(exception));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving STACK_TRACE_HASH data", e);
                }
            }

            // Installation unique ID
            if (crashReportFields.contains(INSTALLATION_ID)) {
                try {
                    //hojjat: i will use channelId-PLATFORM
                    SharedPreferences prefs = BefrestPrefrences.getPrefs(appContext);
                    long uId = prefs.getLong(PREF_U_ID, -1);
                    String chId = prefs.getString(PREF_CH_ID, "chId");
                    String installationId = uId + "-" + chId + "-" + "android";
                    crashReportData.put(INSTALLATION_ID, installationId);
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving INSTALLATION_ID data", e);
                }
            }

            // Device Configuration when crashing
            if (crashReportFields.contains(INITIAL_CONFIGURATION)) {
                try {
                    crashReportData.put(INITIAL_CONFIGURATION, ACRAConfigurationCollector.collectConfiguration(appContext));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving INITIAL_CONFIGURATION data", e);
                }
            }
            if (crashReportFields.contains(CRASH_CONFIGURATION)) {
                try {
                    crashReportData.put(CRASH_CONFIGURATION, ACRAConfigurationCollector.collectConfiguration(appContext));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving CRASH_CONFIGURATION data", e);
                }
            }

            // Collect meminfo
            if (!(exception instanceof OutOfMemoryError) && crashReportFields.contains(DUMPSYS_MEMINFO)) {
                try {
                    crashReportData.put(DUMPSYS_MEMINFO, ACRADumpSysCollector.collectMemInfo());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving DUMPSYS_MEMINFO data", e);
                }
            }

            // Application Package name
            if (crashReportFields.contains(PACKAGE_NAME)) {
                try {
                    crashReportData.put(PACKAGE_NAME, appContext.getPackageName());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving PACKAGE_NAME data", e);
                }
            }

            // Android OS Build details
            if (crashReportFields.contains(BUILD)) {
                try {
                    crashReportData.put(BUILD, ACRAReflectionCollector.collectConstants(Build.class) + ACRAReflectionCollector.collectConstants(Build.VERSION.class, "VERSION"));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving BUILD data", e);
                }
            }

            // Device model
            if (crashReportFields.contains(PHONE_MODEL)) {
                try {
                    crashReportData.put(PHONE_MODEL, Build.MODEL);
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving PHONE_MODEL data", e);
                }
            }
            // Android version
            if (crashReportFields.contains(ANDROID_VERSION)) {
                try {
                    crashReportData.put(ANDROID_VERSION, Build.VERSION.RELEASE);
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving ANDROID_VERSION data", e);
                }
            }

            // Device Brand (manufacturer)
            if (crashReportFields.contains(BRAND)) {
                try {
                    crashReportData.put(BRAND, Build.BRAND);
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving BRAND data", e);
                }
            }
            if (crashReportFields.contains(PRODUCT)) {
                try {
                    crashReportData.put(PRODUCT, Build.PRODUCT);
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving PRODUCT data", e);
                }
            }

            // Device Memory
            if (crashReportFields.contains(TOTAL_MEM_SIZE)) {
                try {
                    crashReportData.put(TOTAL_MEM_SIZE, Long.toString(ACRAReportUtils.getTotalInternalMemorySize()));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving TOTAL_MEM_SIZE data", e);
                }
            }
            if (crashReportFields.contains(AVAILABLE_MEM_SIZE)) {
                try {
                    crashReportData.put(AVAILABLE_MEM_SIZE, Long.toString(ACRAReportUtils.getAvailableInternalMemorySize()));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving AVAILABLE_MEM_SIZE data", e);
                }
            }

            // Application file path
            if (crashReportFields.contains(FILE_PATH)) {
                try {
                    crashReportData.put(FILE_PATH, ACRAReportUtils.getApplicationFilePath(appContext));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving FILE_PATH data", e);
                }
            }

            // Main display details
            if (crashReportFields.contains(DISPLAY)) {
                try {
                    crashReportData.put(DISPLAY, ACRADisplayManagerCollector.collectDisplays(appContext));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving DISPLAY data", e);
                }
            }

            // Add custom info, they are all stored in a single field
            if (crashReportFields.contains(CUSTOM_DATA)) {
                try {
                    crashReportData.put(CUSTOM_DATA, createCustomInfoString(customData));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving CUSTOM_DATA data", e);
                }
            }

            // Device features
            if (crashReportFields.contains(DEVICE_FEATURES)) {
                try {
                    crashReportData.put(DEVICE_FEATURES, ACRADeviceFeaturesCollector.getFeatures(appContext));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving DEVICE_FEATURES data", e);
                }
            }

            // Environment (External storage state)
            if (crashReportFields.contains(ENVIRONMENT)) {
                try {
                    crashReportData.put(ENVIRONMENT, ACRAReflectionCollector.collectStaticGettersResults(Environment.class));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving ENVIRONMENT data", e);
                }
            }

            final ACRASettingsCollector settingsCollector = new ACRASettingsCollector(appContext);
            // System settings
            if (crashReportFields.contains(SETTINGS_SYSTEM)) {
                try {
                    crashReportData.put(SETTINGS_SYSTEM, settingsCollector.collectSystemSettings());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving SETTINGS_SYSTEM data", e);
                }
            }

            // Secure settings
            if (crashReportFields.contains(SETTINGS_SECURE)) {
                try {
                    crashReportData.put(SETTINGS_SECURE, settingsCollector.collectSecureSettings());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving SETTINGS_SECURE data", e);
                }
            }

            // Global settings
            if (crashReportFields.contains(SETTINGS_GLOBAL)) {
                try {

                    crashReportData.put(SETTINGS_GLOBAL, settingsCollector.collectGlobalSettings());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving SETTINGS_GLOBAL data", e);
                }
            }

            // SharedPreferences
            if (crashReportFields.contains(SHARED_PREFERENCES)) {
                try {
                    crashReportData.put(SHARED_PREFERENCES, new ACRASharedPreferencesCollector(appContext).collect());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving SHARED_PREFERENCES data", e);
                }
            }

            // Now get all the crash data that relies on the PackageManager.getPackageInfo()
            // (which may or may not be here).
            try {
                final PackageInfo pi = pm.getPackageInfo();
                if (pi != null) {
                    // Application Version
                    if (crashReportFields.contains(APP_VERSION_CODE)) {
                        crashReportData.put(APP_VERSION_CODE, Integer.toString(pi.versionCode));
                    }
                    if (crashReportFields.contains(APP_VERSION_NAME)) {
                        crashReportData.put(APP_VERSION_NAME, pi.versionName != null ? pi.versionName : "not set");
                    }
                } else {
                    // Could not retrieve package info...
                    crashReportData.put(APP_VERSION_NAME, "Package info unavailable");
                }
            } catch (RuntimeException e) {
                BefLog.e(TAG, "Error while retrieving APP_VERSION_CODE and APP_VERSION_NAME data", e);
            }

            // Retrieve UDID(IMEI) if permission is available
            if (crashReportFields.contains(DEVICE_ID) && pm.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                try {
                    final String deviceId = ACRAReportUtils.getDeviceId(appContext);
                    if (deviceId != null) {
                        crashReportData.put(DEVICE_ID, deviceId);
                    }
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving DEVICE_ID data", e);
                }
            }

            //TODO i can do this!
            // Application specific log file
//            if (crashReportFields.contains(APPLICATION_LOG)) {
//                try {
//                    final String logFile = new LogFileCollector().collectLogFile(context, config.applicationLogFile(), config.applicationLogFileLines());
//                    crashReportData.put(APPLICATION_LOG, logFile);
//                } catch (IOException e) {
//                    BefLog.e(TAG, "Error while reading application log file " + config.applicationLogFile(), e);
//                } catch (RuntimeException e) {
//                    BefLog.e(TAG, "Error while retrieving APPLICATION_LOG data", e);
//
//                }
//            }

            // Media Codecs list
            if (crashReportFields.contains(MEDIA_CODEC_LIST)) {
                try {
                    crashReportData.put(MEDIA_CODEC_LIST, ACRAMediaCodecListCollector.collectMediaCodecList());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving MEDIA_CODEC_LIST data", e);
                }
            }

            // Failing thread details
            if (crashReportFields.contains(THREAD_DETAILS)) {
                try {
                    crashReportData.put(THREAD_DETAILS, ACRAThreadCollector.collect(uncaughtExceptionThread));
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving THREAD_DETAILS data", e);
                }
            }

            // IP addresses
            if (crashReportFields.contains(USER_IP)) {
                try {
                    crashReportData.put(USER_IP, ACRAReportUtils.getLocalIpAddress());
                } catch (RuntimeException e) {
                    BefLog.e(TAG, "Error while retrieving USER_IP data", e);
                }
            }

        } catch (RuntimeException e) {
            BefLog.e(TAG, "Error while retrieving crash data", e);
        }
        BefLog.d(TAG, "creating report data completed.");
        return crashReportData;
    }

    private String createCustomInfoString(Map<String, String> reportCustomData) {
        Map<String, String> params = null;

        if (reportCustomData != null) {
            params = new HashMap<String, String>();
            params.putAll(reportCustomData);
        }

        final StringBuilder customInfo = new StringBuilder();
        for (final Map.Entry<String, String> currentEntry : params.entrySet()) {
            customInfo.append(currentEntry.getKey());
            customInfo.append(" = ");

            // We need to escape new lines in values or they are transformed into new
            // custom fields. => let's replace all '\n' with "\\n"
            final String currentVal = currentEntry.getValue();
            if (currentVal != null) {
                customInfo.append(currentVal.replaceAll("\n", "\\\\n"));
            } else {
                customInfo.append("null");
            }
            customInfo.append("\n");
        }
        return customInfo.toString();
    }

    private String getStackTrace(String msg, Throwable th) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        if (msg != null && !TextUtils.isEmpty(msg)) {
            printWriter.println(msg);
        }

        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        Throwable cause = th;
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        final String stacktraceAsString = result.toString();
        printWriter.close();

        return stacktraceAsString;
    }

    public String collectLogCat(String bufferName) {
        BefLog.d(TAG, "collectLogCat(" + bufferName + ")");

        final int myPid = android.os.Process.myPid();
        String myPidStr = null;
        myPidStr = Integer.toString(myPid) + "):";

        final List<String> commandLine = new ArrayList<String>();
        commandLine.add("logcat");
        if (bufferName != null) {
            commandLine.add("-b");
            commandLine.add(bufferName);
        }

        commandLine.addAll(Arrays.asList(ACRAConstants.DEFAULT_LOGCAT_ARGUMENTS));

        final LinkedList<String> logcatBuf = new ACRABoundedLinkedList<String>(DEFAULT_TAIL_COUNT);

        BufferedReader bufferedReader = null;

        try {
            final Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[commandLine.size()]));
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()), DEFAULT_BUFFER_SIZE_IN_BYTES);

            // Dump stderr to null
            new Thread(new Runnable() {
                public void run() {
                    try {
                        InputStream stderr = process.getErrorStream();
                        byte[] dummy = new byte[DEFAULT_BUFFER_SIZE_IN_BYTES];
                        //noinspection StatementWithEmptyBody
                        while (stderr.read(dummy) >= 0) ;
                    } catch (IOException ignored) {
                    }
                }
            }).start();

            while (true) {
                final String line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                if (myPidStr == null || line.contains(myPidStr)) {
                    logcatBuf.add(line + "\n");
                }
            }

        } catch (IOException e) {
        } finally {
            safeClose(bufferedReader);
        }

        return logcatBuf.toString();
    }

    public static void safeClose(Reader reader) {
        if (reader == null) return;

        try {
            reader.close();
        } catch (IOException e) {
            // We made out best effort to release this resource. Nothing more we can do.
        }
    }

    public static String getTimeString(long time) {
        final SimpleDateFormat format = new SimpleDateFormat(DATE_TIME_FORMAT_STRING, Locale.ENGLISH);
        return format.format(time);
    }

    private String getStackTraceHash(Throwable th) {
        final StringBuilder res = new StringBuilder();
        Throwable cause = th;
        while (cause != null) {
            final StackTraceElement[] stackTraceElements = cause.getStackTrace();
            for (final StackTraceElement e : stackTraceElements) {
                res.append(e.getClassName());
                res.append(e.getMethodName());
            }
            cause = cause.getCause();
        }

        return Integer.toHexString(res.toString().hashCode());
    }
}
