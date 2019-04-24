package bef.rest.befrest.utils;

import android.os.Build;

public class SDKConst {
    //events
    public static final String NETWORK_CONNECTED = "NETWORK_CONNECTED";
    public static final String NETWORK_DISCONNECTED = "NETWORK_DISCONNECTED";
    public static final String CONNECT = "CONNECT";
    public static final String REFRESH = "REFRESH";
    public static final String SERVICE_STOPPED = "SERVICE_STOPPED";
    public static final String RETRY = "RETRY";
    public static final String PING = "PING";
    static final String KEEP_PINGING = "KEEP_PINGING";

    public static final int[] PING_INTERVAL = {30 * 1000, 90 * 1000, 125 * 1000};
    public static final int PING_TIMEOUT = 3 * 1000;
    static final int[] RETRY_INTERVAL = {0, 2 * 1000, 5 * 1000, 10 * 1000, 18 * 1000, 40 * 1000, 100 * 1000, 240 * 1000};
    static final int[] AuthProblemBroadcastDelay = {0, 60 * 1000, 240 * 1000, 600 * 1000};
    public static final String PING_DATA_PREFIX = String.valueOf((int) (Math.random() * 9999));
    public static final int START_ALARM_CODE = 676428;
    public static int prevFailedConnectTries = 0;
    public static int prevAuthProblems;
    public static final String KEY_MESSAGE_PASSED = "KEY_MESSAGE_PASSED";
    static final String BROADCAST_SENDING_PERMISSION_POSTFIX = ".permission.PUSH_SERVICE";
    static final int API_VERSION = 1;
    static final int SDK_VERSION = 2;
    public static final int PUSH = 0;
    public static final int UNAUTHORIZED = 1;
    public static final int CONNECTION_REFRESHED = 2;
    public static final int BEFREST_CONNECTED = 3;
    public static final int BEFREST_CONNECTION_CHANGED = 4;
    public static final String BROADCAST_TYPE = "BROADCAST_TYPE";
    public static final String ACTION_BEFREST_PUSH = "bef.rest.broadcasts.ACTION_BEFREST_PUSH";
    public static final String KEY_TIME_SENT = "KEY_TIME_SENT";
    public static final int TIME_PER_MESSAGE_IN_BATH_MODE = 40;
    public static final int BATCH_MODE_TIMEOUT = 3000;
    public static final int START_SERVICE_AFTER_ILLEGAL_STOP_DELAY = 150 * 1000;
    static final String connectWakeLockName = "BefrestConnectWakeLock";
    static final int JOB_ID = 12698;
    public static final int SDK_INT = Build.VERSION.SDK_INT;
    public static final int OREO_SDK_INT = Build.VERSION_CODES.O;

    public static final int CLOSE_NORMAL = 1;
    public static final int CLOSE_CANNOT_CONNECT = 2;
    public static final int CLOSE_CONNECTION_LOST = 3;
    public static final int CLOSE_PROTOCOL_ERROR = 4;
    public static final int CLOSE_INTERNAL_ERROR = 5;
    public static final int CLOSE_SERVER_ERROR = 6;
    public static final int CLOSE_HANDSHAKE_TIME_OUT = 7;
    public static final int CLOSE_UNAUTHORIZED = 8;
    public static final int CLOSE_CONNECTION_NOT_RESPONDING = 9;
    public static String HANDSHAKE_TIMEOUT_MESSAGE = "Hand shake time out after "
            + 7000 + "ms";
    public static final String PING_TIME_OUT_MESSAGE = "connection did not respond to ping message after " + PING_TIMEOUT + "ms";

    /**
     * Every Detail Will Be Printed In Logcat.
     */
    public static int LOG_LEVEL_VERBOSE = 2;
    /**
     * Data Needed For Debug Will Be Printed.
     */
    public static int LOG_LEVEL_DEBUG = 3;
    /**
     * Standard Level.
     */
    public static int LOG_LEVEL_INFO = 4;
    /**
     * Only Warning And Errors.
     */
    public static int LOG_LEVEL_WARN = 5;
    /**
     * Only Errors.
     */
    public static int LOG_LEVEL_ERROR = 6;
    /**
     * None Of BefrestImpl Logs Will Be Shown.
     */
    public static int LOG_LEVEL_NO_LOG = 100;


}
