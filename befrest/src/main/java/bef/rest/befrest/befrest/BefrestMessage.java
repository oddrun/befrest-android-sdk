package bef.rest.befrest.befrest;


import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.utils.WatchSdk;

import static bef.rest.befrest.utils.AnalyticsType.BEFREST_CONNECTION_CHANGE;
import static bef.rest.befrest.utils.AnalyticsType.CANNOT_CONNECT;
import static bef.rest.befrest.utils.AnalyticsType.CONNECTION_LOST;
import static bef.rest.befrest.utils.AnalyticsType.INVALID_PONG;
import static bef.rest.befrest.utils.AnalyticsType.NETWORK_DISCONNECTED;
import static bef.rest.befrest.utils.AnalyticsType.RETRY;
import static bef.rest.befrest.utils.AnalyticsType.TRY_TO_CONNECT;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;
import static bef.rest.befrest.utils.BefrestPreferences.saveBoolean;
import static bef.rest.befrest.utils.SDKConst.ANALYTIC_CONF;
import static bef.rest.befrest.utils.SDKConst.CRASH_CONF;
import static bef.rest.befrest.utils.SDKConst.INTERRUPTED_EXCEPTION;
import static bef.rest.befrest.utils.SDKConst.IO_EXCEPTION;
import static bef.rest.befrest.utils.SDKConst.JSON_Exception;
import static bef.rest.befrest.utils.SDKConst.SOCKET_EXCEPTION;
import static bef.rest.befrest.utils.SDKConst.SOCKET_TIMEOUT_EXCEPTION;
import static bef.rest.befrest.utils.SDKConst.SSL_EXCEPTION;
import static bef.rest.befrest.utils.SDKConst.URI_SYNTAX_EXCEPTION;
import static bef.rest.befrest.utils.SDKConst.WEB_SOCKET_EXCEPTION;

public final class BefrestMessage implements Parcelable {
    private static final String TAG = "BefrestMessage";

    public enum MsgType {
        NORMAL, BATCH, PONG, TOPIC, GROUP, CONTROLLER
    }

    private MsgType type;
    private String data;
    private String timeStamp;
    private String msgId;
    private boolean isCorrupted;
    private boolean isConfigPush;

    public BefrestMessage(String rawMsg) {
        try {
            JSONObject jsObject = new JSONObject(rawMsg);
            parseMessageV2(jsObject);
            if (MsgType.CONTROLLER.equals(type)) {
                isConfigPush = true;
                parseConfigData();
            }
        } catch (JSONException e) {
            isCorrupted = true;
            reportCorruptedMessageAnomaly(e, rawMsg);
        } catch (Exception e) {
            isCorrupted = true;
            reportCorruptedMessageAnomaly(e, rawMsg);
        }
        if (type == null || timeStamp == null || data == null) {
            isCorrupted = true;
            reportCorruptedMessageAnomaly(new JSONException("type " + type + " timeStamp : " + timeStamp
                    + " data : " + data), rawMsg);
        }
    }

    private void parseConfigData() {
        try {
            if (!Befrest.getInstance().isBefrestInitialized() && getPrefs() == null) {
                BefrestLog.e(TAG, "Befrest is not initialized yet");
                return;
            }
            JSONObject configJson = new JSONObject(data);
            if (configJson.has(ANALYTIC_CONF)) {
                JSONObject analyticConf = configJson.getJSONObject(ANALYTIC_CONF);
                if (analyticConf.has(TRY_TO_CONNECT.name()))
                    saveBoolean(TRY_TO_CONNECT.name(), analyticConf.getBoolean(TRY_TO_CONNECT.name()));
                if (analyticConf.has(CONNECTION_LOST.name()))
                    saveBoolean(CONNECTION_LOST.name(), analyticConf.getBoolean(CONNECTION_LOST.name()));
                if (analyticConf.has(CANNOT_CONNECT.name()))
                    saveBoolean(CANNOT_CONNECT.name(), analyticConf.getBoolean(CANNOT_CONNECT.name()));
                if (analyticConf.has(INVALID_PONG.name()))
                    saveBoolean(INVALID_PONG.name(), analyticConf.getBoolean(INVALID_PONG.name()));
                if (analyticConf.has(NETWORK_DISCONNECTED.name()))
                    saveBoolean(NETWORK_DISCONNECTED.name(), analyticConf.getBoolean(NETWORK_DISCONNECTED.name()));
                if (analyticConf.has(BEFREST_CONNECTION_CHANGE.name()))
                    saveBoolean(BEFREST_CONNECTION_CHANGE.name(), analyticConf.getBoolean(BEFREST_CONNECTION_CHANGE.name()));
                if (analyticConf.has(RETRY.name()))
                    saveBoolean(RETRY.name(), analyticConf.getBoolean(RETRY.name()));
            }
            if (configJson.has(CRASH_CONF)) {
                JSONObject crashConf = configJson.getJSONObject(CRASH_CONF);
                if (crashConf.has(URI_SYNTAX_EXCEPTION))
                    saveBoolean(URI_SYNTAX_EXCEPTION, crashConf.getBoolean(URI_SYNTAX_EXCEPTION));
                if (crashConf.has(JSON_Exception))
                    saveBoolean(JSON_Exception, crashConf.getBoolean(JSON_Exception));
                if (crashConf.has(WEB_SOCKET_EXCEPTION))
                    saveBoolean(WEB_SOCKET_EXCEPTION, crashConf.getBoolean(WEB_SOCKET_EXCEPTION));
                if (crashConf.has(SOCKET_EXCEPTION))
                    saveBoolean(URI_SYNTAX_EXCEPTION, crashConf.getBoolean(URI_SYNTAX_EXCEPTION));
                if (crashConf.has(IO_EXCEPTION))
                    saveBoolean(IO_EXCEPTION, crashConf.getBoolean(IO_EXCEPTION));
                if (crashConf.has(INTERRUPTED_EXCEPTION))
                    saveBoolean(INTERRUPTED_EXCEPTION, crashConf.getBoolean(INTERRUPTED_EXCEPTION));
                if (crashConf.has(SOCKET_TIMEOUT_EXCEPTION))
                    saveBoolean(SOCKET_TIMEOUT_EXCEPTION, crashConf.getBoolean(SOCKET_TIMEOUT_EXCEPTION));
                if (crashConf.has(SSL_EXCEPTION))
                    saveBoolean(SSL_EXCEPTION, crashConf.getBoolean(SSL_EXCEPTION));

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void reportCorruptedMessageAnomaly(Exception e, String rawMsg) {
        WatchSdk.reportCrash(e, rawMsg);
    }

    private void parseMessageV2(JSONObject jsObject) throws JSONException {
        try {
            msgId = jsObject.getString("mid");
        } catch (JSONException e) {
            msgId = null;
        } catch (Exception e) {
            msgId = null;
        }
        parseMessageV1(jsObject);
    }

    private void parseMessageV1(JSONObject jsObject) throws JSONException {
        switch (jsObject.getString("t")) {
            case "0":
                type = MsgType.PONG;
                break;
            case "1":
                type = MsgType.NORMAL;
                break;
            case "2":
                type = MsgType.BATCH;
                break;
            case "3":
                type = MsgType.TOPIC;
                break;
            case "4":
                type = MsgType.GROUP;
            case "5":
                type = MsgType.CONTROLLER;
                break;
            default:
                throw new JSONException("unKnown Push Type!");
        }
        data = Util.decodeBase64(jsObject.getString("m"));
        timeStamp = jsObject.getString("ts");
    }

    public String getData() {
        return data;
    }

    public String getTimeStamp() {
        return timeStamp;
    }


    public String getAckMessage() {
        return "A" + type.toString().toUpperCase().charAt(0) + msgId;
    }

    @Override
    public String toString() {
        return " data: " + data +
                "        time: " + timeStamp;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(data);
        dest.writeString(timeStamp);
    }

    private BefrestMessage(Parcel source) {
        data = source.readString();
        timeStamp = source.readString();
    }

    public boolean isCorrupted() {
        return isCorrupted;
    }

    public boolean isConfigPush() {
        return isConfigPush;
    }

    public MsgType getType() {
        return type;
    }

    public String getMsgId() {
        return msgId;
    }

    public static final Creator CREATOR =
            new Creator() {

                @Override
                public BefrestMessage createFromParcel(Parcel source) {
                    return new BefrestMessage(source);
                }

                @Override
                public BefrestMessage[] newArray(int size) {
                    return new BefrestMessage[size];
                }

            };
}