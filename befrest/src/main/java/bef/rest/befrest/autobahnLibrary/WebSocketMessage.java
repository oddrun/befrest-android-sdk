package bef.rest.befrest.autobahnLibrary;

import java.util.List;

import bef.rest.befrest.utils.NameValuePair;

/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
public class WebSocketMessage {

    public static class Message {

    }

    public static class Quit extends Message {
    }


    public static class ClientHandshake extends Message {

        String mHost;
        public String mPath;
        public String mQuery;
        String mOrigin;
        public String[] mSubProtocols;
        public List<NameValuePair> mHeaderList;

        public ClientHandshake(String host) {
            mHost = host;
            mPath = "/";
            mOrigin = null;
            mSubProtocols = null;
            mHeaderList = null;
        }
    }

    public static class AckMessage extends TextMessage {


        public AckMessage(String payload) {
            super(payload);
        }
    }

    public static class ServerHandshake extends Message {
        public boolean mSuccess;

        ServerHandshake(boolean success) {
            mSuccess = success;
        }
    }

    public static class ConnectionLost extends Message {
        ConnectionLost() {
        }
    }

    public static class ServerError extends Message {
        public int mStatusCode;
        public String mStatusMessage;

        ServerError(int statusCode, String statusMessage) {
            mStatusCode = statusCode;
            mStatusMessage = statusMessage;
        }

    }

    public static class ProtocolViolation extends Message {

        WebSocketException mException;

        ProtocolViolation(WebSocketException e) {
            mException = e;
        }
    }

    /// An exception occurred in the WS reader or WS writer.
    public static class Error extends Message {

        Exception mException;

        Error(Exception e) {
            mException = e;
        }
    }

    public static class TextMessage extends Message {

        public String payload;

        public TextMessage(String payload) {
            this.payload = payload;
        }
    }

    static class RawTextMessage extends Message {

        byte[] mPayload;

        RawTextMessage(byte[] payload) {
            mPayload = payload;
        }
    }

    static class BinaryMessage extends Message {

        byte[] mPayload;

        BinaryMessage(byte[] payload) {
            mPayload = payload;
        }
    }

    public static class Close extends Message {

        public int code;
        public String reason;

        Close(int code, String reason) {
            this.code = code;
            this.reason = reason;
        }
    }

    /// WebSockets ping to send or received.
    public static class Ping extends Message {

        byte[] payload;

        public Ping(byte[] payload) {
            this.payload = payload;
        }
    }

    /// WebSockets pong to send or received.
    public static class Pong extends Message {

        public byte[] payload;


        Pong(byte[] payload) {
            this.payload = payload;
        }
    }

    public static class Redirect extends Message {

        public String location;

        Redirect(String location) {
            this.location = location;
        }
    }

}
