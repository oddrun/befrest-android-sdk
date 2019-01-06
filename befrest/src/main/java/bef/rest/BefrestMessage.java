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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public final class BefrestMessage implements Parcelable {
    private static final String TAG = BefLog.TAG_PREF + "BefrestMessage";

    /* package */ enum MsgType {
        NORMAL, BATCH, PONG, TOPIC, GROUP;
    }

    /* package */ MsgType type;
    /* package */ String data;
    /* package */ String timeStamp;
    /* package */ String msgId;
    /* package */ boolean isCorrupted;

    /* package */ BefrestMessage(Context appContext, String rawMsg) {
        try {
            JSONObject jsObject = new JSONObject(rawMsg);
            parseMessageV2(jsObject);
        } catch (Exception e) { //JSONException or any other unExpected Exception
            isCorrupted = true;
            BefrestImpl.sendCrash(e.getMessage());
            reportCorruptedMessageAnomaly(appContext, e);
        }
        //last check if message is not properly parsed
        if (type == null || timeStamp == null || data == null) {
            isCorrupted = true;
            reportCorruptedMessageAnomaly(appContext, null);
        }
    }

    private void reportCorruptedMessageAnomaly(Context c, Exception e) {

    }

    private void parseMessageV2(JSONObject jsObject) throws JSONException {
        try {
            msgId = jsObject.getString("mid"); //if message is version 2
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
        data = BefrestImpl.Util.decodeBase64(jsObject.getString("m"));
        timeStamp = jsObject.getString("ts");
    }

    public String getData() {
        return data;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    /* package */ String getAckMessage() {
        return "A" + type.toString().charAt(0) + msgId;
    }

    /**
     *
     * @return String
     */


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append(" data: ").append(data)
                .append("        time: ").append(timeStamp).toString();
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

    public BefrestMessage(Parcel source) {
        data = source.readString();
        timeStamp = source.readString();
    }

    public static final Parcelable.Creator CREATOR =
            new Parcelable.Creator() {

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