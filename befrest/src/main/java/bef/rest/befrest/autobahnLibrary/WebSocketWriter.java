/*
 Copyright 2015-2016 Befrest
 <p/>
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 <p/>
 http://www.apache.org/licenses/LICENSE-2.0
 <p/>
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package bef.rest.befrest.autobahnLibrary;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;

import bef.rest.befrest.utils.AnalyticsType;
import bef.rest.befrest.utils.NameValuePair;
import bef.rest.befrest.utils.WatchSdk;


/*
 * SocketCallBacks writer, the sending leg of a WebSockets connection.
 * This is run on it's background thread with it's own message loop.
 * The only method that needs to be called (from foreground thread) is forward(),
 * which is used to forward a WebSockets message to this object (running on
 * background thread) so that it can be formatted and sent out on the
 * underlying TCP socket.
 */
public class WebSocketWriter extends Handler {

    private final static String CRLF = "\r\n";
    private final Random mRng = new Random();
    private final Handler mMaster;
    private final Looper mLooper;
    private final WebSocketOptions mOptions;
    private ByteBufferOutputStream mBuffer;
    private Socket mSocket;
    private boolean mActive;
    /**
     * Create new WebSockets background writer.
     *
     * @param looper  The message looper of the background thread on which
     *                this object is running.
     * @param master  The message handler of master (foreground thread).
     * @param socket  The socket channel created on foreground thread.
     * @param options WebSockets connection options.
     */
    public WebSocketWriter(Looper looper, Handler master, Socket socket, WebSocketOptions options)
            throws IOException {
        super(looper);
        mLooper = looper;
        mMaster = master;
        mOptions = options;
        mSocket = socket;
        mBuffer = new ByteBufferOutputStream(options.getMaxFramePayloadSize() + 14, 4 * 64 * 1024);
        mActive = true;
        mSocket.setSendBufferSize(2 * 1024 * 1024);

    }

    @Override
    public void handleMessage(Message msg) {
        try {
            mBuffer.clear();
            processMessage(msg.obj);
            setupWriter();
        } catch (SocketException e) {
            notify(new WebSocketMessage.ConnectionLost());
            WatchSdk.reportCrash(e, null);
            WatchSdk.reportAnalytics(AnalyticsType.CONNECTION_LOST, "Socket Exception Happen");
        } catch (Exception e) {
            notify(new Error(e));
        }
    }

    private void setupWriter() throws IOException {
        mBuffer.flip();
        if (mBuffer.remaining() > 0) {
            byte arr[] = new byte[mBuffer.remaining()];
            mBuffer.getBuffer().get(arr);
            mSocket.getOutputStream().write(arr);
            mSocket.getOutputStream().flush();
        }
    }

    private void write(String stringToWrite) {
        try {
            mBuffer.write(stringToWrite.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void write(byte byteToWrite) {
        try {
            mBuffer.write(byteToWrite);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void write(byte[] bytesToWrite) {
        try {
            mBuffer.write(bytesToWrite);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call this from the foreground (UI) thread to make the writer
     * (running on background thread) send a SocketCallBacks message on the
     * underlying TCP.
     *
     * @param message Message to send to WebSockets writer. An instance of a subclass of
     *                Message  or another type which then needs to be handled within
     *                processAppMessage() (in a class derived from this class).
     */
    public void forward(Object message) {
        if (!mActive) {
            return;
        }
        Message msg = obtainMessage();
        msg.obj = message;
        sendMessage(msg);
    }


    /**
     * Notify the master (foreground thread).
     *
     * @param message Message to send to master.
     */
    private void notify(Object message) {
        Message msg = mMaster.obtainMessage();
        msg.obj = message;
        mMaster.sendMessage(msg);
    }


    /**
     * Create new key for WebSockets handshake.
     *
     * @return WebSockets handshake key (Base64 encoded).
     */
    private String newHandshakeKey() {
        final byte[] ba = new byte[16];
        mRng.nextBytes(ba);
        return Base64.encodeToString(ba, Base64.NO_WRAP);
    }


    /**
     * Create new (random) frame mask.
     *
     * @return Frame mask (4 octets).
     */
    private byte[] newFrameMask() {
        final byte[] ba = new byte[4];
        mRng.nextBytes(ba);
        return ba;
    }


    /**
     * Send SocketCallBacks client handshake.
     */
    private void sendClientHandshake(WebSocketMessage.ClientHandshake message) throws IOException {
        String path;
        if (message.mQuery != null) {
            path = message.mPath + "?" + message.mQuery;
        } else {
            path = message.mPath;
        }
        write("GET " + path + " HTTP/1.1");
        write(CRLF);
        write("Host: " + message.mHost);
        write(CRLF);
        write("Upgrade: websocket");
        write(CRLF);
        write("Connection: Upgrade");
        write(CRLF);

        write("Sec-WebSocket-Key: " + newHandshakeKey());
        write(CRLF);

        if (message.mOrigin != null && !message.mOrigin.equals("")) {
            write("Origin: " + message.mOrigin);
            write(CRLF);
        }

        if (message.mSubProtocols != null && message.mSubProtocols.length > 0) {
            write("Sec-WebSocket-Protocol: ");
            for (int i = 0; i < message.mSubProtocols.length; ++i) {
                write(message.mSubProtocols[i]);
                if (i != message.mSubProtocols.length - 1) {
                    write(", ");
                }
            }
            write(CRLF);
        }

        write("Sec-WebSocket-Version: 13");
        write(CRLF);

        // Header injection
        if (message.mHeaderList != null) {
            for (NameValuePair pair : message.mHeaderList) {
                mBuffer.write((pair.getName() + ":" + pair.getValue()).getBytes());
                write(CRLF);
            }
        }
        write(CRLF);
    }

    /**
     * Send WebSockets close.
     */
    private void sendClose(WebSocketMessage.Close message) throws IOException, WebSocketException {

        if (message.code > 0) {

            byte[] payload;

            if (message.reason != null && !message.reason.equals("")) {
                byte[] pReason = message.reason.getBytes("UTF-8");
                payload = new byte[2 + pReason.length];
                System.arraycopy(pReason, 0, payload, 2, pReason.length);
            } else {
                payload = new byte[2];
            }

            if (payload.length > 125) {
                throw new WebSocketException("close payload exceeds 125 octets");
            }

            payload[0] = (byte) ((message.code >> 8) & 0xff);
            payload[1] = (byte) (message.code & 0xff);

            sendFrame(8, payload);

        } else {

            sendFrame(8, null);
        }
    }

    /**
     * Send WebSockets ping.
     */
    private void sendPing(WebSocketMessage.Ping message) throws IOException, WebSocketException {
        if (message.payload != null && message.payload.length > 125) {
            throw new WebSocketException("ping payload exceeds 125 octets");
        }
        sendFrame(9, message.payload);
    }

    /**
     * Send WebSockets pong. Normally, unsolicited Pongs are not used,
     * but Pongs are only send in response to a Ping from the peer.
     */
    private void sendPong(WebSocketMessage.Pong message) throws IOException, WebSocketException {
        if (message.payload != null && message.payload.length > 125) {
            throw new WebSocketException("pong payload exceeds 125 octets");
        }
        sendFrame(10, message.payload);
    }

    /**
     * Send WebSockets binary message.
     */
    private void sendBinaryMessage(WebSocketMessage.BinaryMessage message) throws IOException, WebSocketException {
        if (message.mPayload.length > mOptions.getMaxMessagePayloadSize()) {
            throw new WebSocketException("message payload exceeds payload limit");
        }
        sendFrame(2, message.mPayload);
    }

    /**
     * Send WebSockets text message.
     */
    private void sendTextMessage(WebSocketMessage.TextMessage message) throws IOException, WebSocketException {
        byte[] payload = message.payload.getBytes("UTF-8");
        if (payload.length > mOptions.getMaxMessagePayloadSize()) {
            Log.i("AckTest", "sendTextMessage: exception");
            throw new WebSocketException("message payload exceeds payload limit");
        }
        sendFrame(1, payload);
    }

    /**
     * Send WebSockets binary message.
     */
    private void sendRawTextMessage(WebSocketMessage.RawTextMessage message) throws IOException, WebSocketException {
        if (message.mPayload.length > mOptions.getMaxMessagePayloadSize()) {
            throw new WebSocketException("message payload exceeds payload limit");
        }
        sendFrame(1, message.mPayload);
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        if (payload != null) {
            sendFrame(opcode, payload, payload.length);
        } else {
            sendFrame(opcode, null, 0);
        }
    }

    /**
     * Sends a WebSockets frame. Only need to use this method in derived classes which implement
     * more message types in processAppMessage(). You need to know what you are doing!
     *
     * @param opcode  The SocketCallBacks frame opcode.
     * @param payload Frame payload or null.
     * @param length  Length of the chunk within payload to send.
     */
    private void sendFrame(int opcode, byte[] payload, int length) throws IOException {

        // first octet
        byte b0 = 0;
        b0 |= (byte) (1 << 7);
        b0 |= (byte) opcode;
        write(b0);

        // second octet
        byte b1 = 0;
        if (mOptions.getMaskClientFrames()) {
            b1 = (byte) (1 << 7);
        }

        // extended payload length
        if ((long) length <= 125) {
            b1 |= (byte) (long) length;
            write(b1);
        } else if ((long) length <= 0xffff) {
            b1 |= (byte) (126 & 0xff);
            write(b1);
            write(new byte[]{(byte) (((long) length >> 8) & 0xff),
                    (byte) ((long) length & 0xff)});
        } else {
            b1 |= (byte) (127 & 0xff);
            write(b1);
            write(new byte[]{(byte) (((long) length >> 56) & 0xff),
                    (byte) (((long) length >> 48) & 0xff),
                    (byte) (((long) length >> 40) & 0xff),
                    (byte) (((long) length >> 32) & 0xff),
                    (byte) (((long) length >> 24) & 0xff),
                    (byte) (((long) length >> 16) & 0xff),
                    (byte) (((long) length >> 8) & 0xff),
                    (byte) ((long) length & 0xff)});
        }

        byte mask[] = null;
        if (mOptions.getMaskClientFrames()) {
            // a mask is always needed, even without payload
            mask = newFrameMask();
            write(mask[0]);
            write(mask[1]);
            write(mask[2]);
            write(mask[3]);
        }

        if ((long) length > 0) {
            if (mOptions.getMaskClientFrames()) {
                /// \todo optimize masking
                /// \todo masking within buffer of output stream
                for (int i = 0; i < (long) length; ++i) {
                    assert mask != null;
                    payload[i] ^= mask[i % 4];
                }
            }
            mBuffer.write(payload, 0, length);
        }
    }


    /**
     * Process message received from foreground thread. This is called from
     * the message looper set up for the background thread running this writer.
     *
     * @param msg Message from thread message queue.
     */


    /**
     * Process WebSockets or control message from master. Normally,
     * there should be no reason to override this. If you do, you
     * need to know what you are doing.
     *
     * @param msg An instance of the Message subclass or a message
     *            that is handled in processAppMessage().
     */
    private void processMessage(Object msg) throws IOException, WebSocketException {

        if (msg instanceof WebSocketMessage.TextMessage) {

            sendTextMessage((WebSocketMessage.TextMessage) msg);

        } else if (msg instanceof WebSocketMessage.RawTextMessage) {

            sendRawTextMessage((WebSocketMessage.RawTextMessage) msg);

        } else if (msg instanceof WebSocketMessage.BinaryMessage) {

            sendBinaryMessage((WebSocketMessage.BinaryMessage) msg);

        } else if (msg instanceof WebSocketMessage.Ping) {

            sendPing((WebSocketMessage.Ping) msg);

        } else if (msg instanceof WebSocketMessage.Pong) {

            sendPong((WebSocketMessage.Pong) msg);

        } else if (msg instanceof WebSocketMessage.Close) {

            sendClose((WebSocketMessage.Close) msg);

        } else if (msg instanceof WebSocketMessage.ClientHandshake) {
            sendClientHandshake((WebSocketMessage.ClientHandshake) msg);

        } else if (msg instanceof WebSocketMessage.Quit) {
            mLooper.quit();
            mActive = false;

        } else {
            processAppMessage();
        }
    }


    /**
     * Process message other than plain WebSockets or control message.
     * This is intended to be overridden in derived classes.
     */
    private void processAppMessage() throws WebSocketException {

        throw new WebSocketException("unknown message received by WebSocketWriter");

    }

    public void flush() {
        mBuffer.clear();
        mBuffer.getBuffer().clear();
        try {
            mBuffer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
