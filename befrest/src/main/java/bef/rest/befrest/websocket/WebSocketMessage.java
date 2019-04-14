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


package bef.rest.befrest.websocket;

import java.util.List;

import bef.rest.befrest.utils.NameValuePair;


public class WebSocketMessage {

   public static class Message {

      int senderId;
   }

   public static class Quit extends Message {
   }


   public static class ClientHandshake extends Message {

      public String mHost;
      public String mPath;
      public String mQuery;
      public String mOrigin;
      public String[] mSubprotocols;
      public List<NameValuePair> mHeaderList;

      ClientHandshake(String host) {
         mHost = host;
         mPath = "/";
         mOrigin = null;
         mSubprotocols = null;
         mHeaderList = null;
      }
   }

   public static class AckMessage extends TextMessage{


      public AckMessage(String payload) {
         super(payload);
      }
   }

   public static class ServerHandshake extends Message {
	   public boolean mSuccess;
	   
	   public ServerHandshake(boolean success) {
		   mSuccess = success;
	   }
   }

   public static class ConnectionLost extends Message {
      public ConnectionLost() {
      }
   }
   
   public static class ServerError extends Message {
	   public int mStatusCode;
	   public String mStatusMessage;
	   
	   public ServerError(int statusCode, String statusMessage) {
		   mStatusCode = statusCode;
		   mStatusMessage = statusMessage;
	   }
	   
   }

   public static class ProtocolViolation extends Message {

      public WebSocketException mException;

      public ProtocolViolation(WebSocketException e) {
         mException = e;
      }
   }

   /// An exception occured in the WS reader or WS writer.
   public static class Error extends Message {

      public Exception mException;

      public Error(Exception e) {
         mException = e;
      }
   }

   public static class TextMessage extends Message {

      public String mPayload;

      public TextMessage(String payload) {
         mPayload = payload;
      }
   }

   public static class RawTextMessage extends Message {

      public byte[] mPayload;

      RawTextMessage(byte[] payload) {
         mPayload = payload;
      }
   }

   public static class BinaryMessage extends Message {

      public byte[] mPayload;

      BinaryMessage(byte[] payload) {
         mPayload = payload;
      }
   }

   public static class Close extends Message {

      public int mCode;
      public String mReason;

      Close() {
         mCode = -1;
         mReason = null;
      }

      Close(int code) {
         mCode = code;
         mReason = null;
      }

      Close(int code, String reason) {
         mCode = code;
         mReason = reason;
      }
   }

   /// WebSockets ping to send or received.
   public static class Ping extends Message {

      public byte[] mPayload;

      Ping() {
         mPayload = null;
      }

      public Ping(byte[] payload) {
         mPayload = payload;
      }
   }

   /// WebSockets pong to send or received.
   public static class Pong extends Message {

      public byte[] mPayload;

      Pong() {
         mPayload = null;
      }

      Pong(byte[] payload) {
         mPayload = payload;
      }
   }

}
