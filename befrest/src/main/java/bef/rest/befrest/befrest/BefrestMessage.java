package bef.rest.befrest.befrest;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import bef.rest.befrest.utils.AnalyticsType;
import bef.rest.befrest.utils.Util;
import bef.rest.befrest.utils.WatchSdk;

public final class BefrestMessage implements Parcelable {

    public enum MsgType {
        NORMAL, BATCH, PONG, TOPIC, GROUP
    }

    private MsgType type;
    private String data;
    private String timeStamp;
    private String msgId;
    private boolean isCorrupted;

    public BefrestMessage(String rawMsg) {
        try {
            JSONObject jsObject = new JSONObject(rawMsg);
            parseMessageV2(jsObject);
        } catch (Exception e) {
            isCorrupted = true;
            reportCorruptedMessageAnomaly(e,rawMsg);
        }
        if (type == null || timeStamp == null || data == null) {
            isCorrupted = true;
            reportCorruptedMessageAnomaly(new Exception("type " + type + " timeStamp : " + timeStamp
                    + " data : " + data), rawMsg);
        }
    }


    private void reportCorruptedMessageAnomaly(Exception e, String rawMsg) {
        WatchSdk.reportAnalytics(AnalyticsType.MALFORMED_DATA, e);
        WatchSdk.reportCrash(e,rawMsg);
    }

    private void parseMessageV2(JSONObject jsObject) throws JSONException {
        try {
            msgId = jsObject.getString("mid");
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