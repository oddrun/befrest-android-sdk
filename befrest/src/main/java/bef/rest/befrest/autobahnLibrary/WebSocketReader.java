/*
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


package bef.rest.befrest.autobahnLibrary;

import android.os.Handler;
import android.os.Message;
import android.util.Pair;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import bef.rest.befrest.utils.AnalyticsType;
import bef.rest.befrest.utils.Utf8Validator;
import bef.rest.befrest.utils.WatchSdk;

import static bef.rest.befrest.utils.SDKConst.CLOSE_CONNECTION_LOST;
import static bef.rest.befrest.utils.SDKConst.CLOSE_INTERNAL_ERROR;
import static bef.rest.befrest.utils.SDKConst.CLOSE_PROTOCOL_ERROR;


/**
 * SocketCallBacks reader, the receiving leg of a WebSockets connection.
 * This runs on it's own background thread and posts messages to master
 * thread's message queue for there to be consumed by the application.
 * The only method that needs to be called (from foreground thread) is quit(),
 * which gracefully shuts down the background receiver thread.
 */
@SuppressWarnings("CharsetObjectCanBeUsed")
public class WebSocketReader extends Thread {

    private final Handler mMaster;
    private final WebSocketOptions mOptions;

    private BufferedInputStream mBufferedStream;
    private Socket mSocket;
    private int mPosition;
    private byte[] mMessageData;
    private ByteArrayOutputStream mMessagePayload;

    private final static int STATE_CLOSED = 0;
    private final static int STATE_CONNECTING = 1;
    private final static int STATE_CLOSING = 2;
    private final static int STATE_OPEN = 3;

    private boolean mStopped = false;
    private int mState;


    private boolean mInsideMessage = false;
    private int mMessageOpcode;

    /// Frame currently being received.
    private FrameHeader mFrameHeader;

    private Utf8Validator mUtf8Validator = new Utf8Validator();


    /**
     * WebSockets frame metadata.
     */
    private static class FrameHeader {
        int mOpcode;
        boolean mFin;
        int mReserved;
        int mHeaderLen;
        int mPayloadLen;
        int mTotalLen;
        byte[] mMask;
    }


    /**
     * Create new WebSockets background reader.
     *
     * @param master The message handler of master (foreground thread).
     * @param socket The socket channel created on foreground thread.
     */
    public WebSocketReader(Handler master, Socket socket, WebSocketOptions options, String threadName)
            throws IOException {
        super(threadName);
        mMaster = master;
        mOptions = options;
        mSocket = socket;
        mMessageData = new byte[mOptions.getMaxFramePayloadSize() + 14];
        mBufferedStream = new BufferedInputStream(mSocket.getInputStream(), mOptions.getMaxFramePayloadSize() + 14);
        mMessagePayload = new ByteArrayOutputStream(options.getMaxMessagePayloadSize());
        mFrameHeader = null;
        mState = STATE_CONNECTING;

    }

    /**
     * Graceful shutdown of background reader thread (called from master).
     */
    public void quit() {
        mState = STATE_CLOSED;
    }


    /**
     * Notify the master (foreground thread) of WebSockets message received
     * and unwrapped.
     *
     * @param message Message to send to master.
     */
    private void notify(Object message) {
        Message msg = mMaster.obtainMessage();
        msg.obj = message;
        mMaster.sendMessage(msg);
    }

    /**
     * Process incoming WebSockets data (after handshake).
     */
    private boolean processData() throws Exception {

        // outside frame?
        if (mFrameHeader == null) {
            // need at least 2 bytes from WS frame header to start processing
            if (mPosition >= 2) {

                byte b0 = mMessageData[0];
                boolean fin = (b0 & 0x80) != 0;
                int rsv = (b0 & 0x70) >> 4;
                int opcode = b0 & 0x0f;

                byte b1 = mMessageData[1];
                boolean masked = (b1 & 0x80) != 0;
                int payload_len1 = b1 & 0x7f;
                // now check protocol compliance
                if (rsv != 0) {
                    throw new WebSocketException("RSV != 0 and no extension negotiated");
                }
                if (masked) {
                    // currently, we don't allow this. need to see whats the final spec.
                    throw new WebSocketException("masked server frame");
                }
                if (opcode > 7) {
                    // control frame
                    if (!fin) {
                        throw new WebSocketException("fragmented control frame");
                    }
                    if (payload_len1 > 125) {
                        throw new WebSocketException("control frame with payload length > 125 octets");
                    }
                    if (opcode != 8 && opcode != 9 && opcode != 10) {
                        throw new WebSocketException("control frame using reserved opcode " + opcode);
                    }
                    if (opcode == 8 && payload_len1 == 1) {
                        throw new WebSocketException("received close control frame with payload len 1");
                    }
                } else {
                    // message frame
                    if (opcode != 0 && opcode != 1 && opcode != 2) {
                        throw new WebSocketException("data frame using reserved opcode " + opcode);
                    }
                    if (!mInsideMessage && opcode == 0) {
                        throw new WebSocketException("received continuation data frame outside fragmented message");
                    }
                    if (mInsideMessage && opcode != 0) {
                        throw new WebSocketException("received non-continuation data frame while inside fragmented message");
                    }
                }

                int mask_len = 0;
                int header_len;

                if (payload_len1 < 126) {
                    header_len = 2 + mask_len;
                } else if (payload_len1 == 126) {
                    header_len = 2 + 2 + mask_len;
                } else {
                    header_len = 2 + 8 + mask_len;
                }

                // continue when complete frame header is available
                if (mPosition >= header_len) {

                    // determine frame payload length
                    int i = 2;
                    long payload_len;
                    if (payload_len1 == 126) {
                        payload_len = ((0xff & mMessageData[i]) << 8) | (0xff & mMessageData[i + 1]);
                        if (payload_len < 126) {
                            throw new WebSocketException("invalid data frame length (not using minimal length encoding)");
                        }
                    } else if (payload_len1 == 127) {
                        if ((0x80 & mMessageData[i]) != 0) {
                            throw new WebSocketException("invalid data frame length (> 2^63)");
                        }
                        payload_len = ((long) (0xff & mMessageData[i]) << 56) |
                                ((long) (0xff & mMessageData[i + 1]) << 48) |
                                ((long) (0xff & mMessageData[i + 2]) << 40) |
                                ((long) (0xff & mMessageData[i + 3]) << 32) |
                                ((long) (0xff & mMessageData[i + 4]) << 24) |
                                ((long) (0xff & mMessageData[i + 5]) << 16) |
                                ((long) (0xff & mMessageData[i + 6]) << 8) |
                                ((long) (0xff & mMessageData[i + 7]));
                        if (payload_len < 65536) {
                            throw new WebSocketException("invalid data frame length (not using minimal length encoding)");
                        }
                    } else {
                        payload_len = payload_len1;
                    }

                    // immediately bail out on frame too large
                    if (payload_len > mOptions.getMaxFramePayloadSize()) {
                        throw new WebSocketException("frame payload too large");
                    }

                    // save frame header metadata
                    mFrameHeader = new FrameHeader();
                    mFrameHeader.mOpcode = opcode;
                    mFrameHeader.mFin = fin;
                    mFrameHeader.mReserved = rsv;
                    mFrameHeader.mPayloadLen = (int) payload_len;
                    mFrameHeader.mHeaderLen = header_len;
                    mFrameHeader.mTotalLen = mFrameHeader.mHeaderLen + mFrameHeader.mPayloadLen;
                    mFrameHeader.mMask = null;

                    // continue processing when payload empty or completely buffered
                    return mFrameHeader.mPayloadLen == 0 || mPosition >= mFrameHeader.mTotalLen;

                } else {

                    // need more data
                    return false;
                }
            } else {

                // need more data
                return false;
            }

        } else {
            /// \todo refactor this for streaming processing, incl. fail fast on invalid UTF-8 within frame already
            // within frame

            // see if we buffered complete frame
            if (mPosition >= mFrameHeader.mTotalLen) {

                // cut out frame payload
                byte[] framePayload = null;
                if (mFrameHeader.mPayloadLen > 0) {
                    framePayload = new byte[mFrameHeader.mPayloadLen];
                    System.arraycopy(mMessageData, mFrameHeader.mHeaderLen, framePayload, 0, mFrameHeader.mPayloadLen);
                }
                mMessageData = Arrays.copyOfRange(mMessageData, mFrameHeader.mTotalLen, mMessageData.length + mFrameHeader.mTotalLen);
                mPosition -= mFrameHeader.mTotalLen;

                if (mFrameHeader.mOpcode > 7) {
                    // control frame
                    if (mFrameHeader.mOpcode == 8) {

                        int code = 1005; // CLOSE_STATUS_CODE_NULL : no status code received
                        String reason = null;

                        if (mFrameHeader.mPayloadLen >= 2) {

                            assert framePayload != null;
                            code = (framePayload[0] & 0xff) * 256 + (framePayload[1] & 0xff);
                            if (code < 1000 || code <= 2999 && code != 1000 && code != 1001 && code != 1002 && code != 1003 && code != 1007 && code != 1008 && code != 1009 && code != 1010 && code != 1011 || code >= 5000) {

                                throw new WebSocketException("invalid close code " + code);
                            }

                            // parse and check close reason
                            if (mFrameHeader.mPayloadLen > 2) {

                                byte[] ra = new byte[mFrameHeader.mPayloadLen - 2];
                                System.arraycopy(framePayload, 2, ra, 0, mFrameHeader.mPayloadLen - 2);
                                Utf8Validator val = new Utf8Validator();
                                val.validate(ra);
                                if (val.isInvalid()) {
                                    throw new WebSocketException("invalid close reasons (not UTF-8)");
                                } else {
                                    reason = new String(ra, "UTF-8");
                                }
                            }
                        }
                        onClose(code, reason);
                        // We have received a close, so lets set the state as early as possible.
                        // It seems that Handler() has a lag to deliver a message, so if the onClose
                        // is sent to master and just after that the other peer closes the socket,
                        // BufferedInputReader.read() will throw an exception which results in
                        // our code sending a ConnectionLost() message to master.
                        mState = STATE_CLOSED;

                    } else if (mFrameHeader.mOpcode == 9) {
                        // dispatch WS ping
                        onPing(framePayload);

                    } else if (mFrameHeader.mOpcode == 10) {
                        // dispatch WS pong
                        onPong(framePayload);

                    } else {

                        // should not arrive here (handled before)
                        throw new Exception("logic error");
                    }

                } else {
                    // message frame

                    if (!mInsideMessage) {
                        // new message started
                        mInsideMessage = true;
                        mMessageOpcode = mFrameHeader.mOpcode;
                        if (mMessageOpcode == 1 && mOptions.getValidateIncomingUtf8()) {
                            mUtf8Validator.reset();
                        }
                    }

                    if (framePayload != null) {

                        // immediately bail out on message too large
                        if (mMessagePayload.size() + framePayload.length > mOptions.getMaxMessagePayloadSize()) {
                            throw new WebSocketException("message payload too large");
                        }

                        // validate incoming UTF-8
                        if (mMessageOpcode == 1 && mOptions.getValidateIncomingUtf8() && !mUtf8Validator.validate(framePayload)) {
                            throw new WebSocketException("invalid UTF-8 in text message payload");
                        }

                        // buffer frame payload for message
                        mMessagePayload.write(framePayload);
                    }

                    // on final frame ..
                    if (mFrameHeader.mFin) {

                        if (mMessageOpcode == 1) {

                            // verify that UTF-8 ends on codepoint
                            if (mOptions.getValidateIncomingUtf8() && mUtf8Validator.isInvalid()) {
                                throw new WebSocketException("UTF-8 text message payload ended within Unicode code point");
                            }

                            // deliver text message
                            if (mOptions.getReceiveTextMessagesRaw()) {

                                // dispatch WS text message as raw (but validated) UTF-8
                                onRawTextMessage(mMessagePayload.toByteArray());

                            } else {

                                // dispatch WS text message as Java String (previously already validated)
                                String s = new String(mMessagePayload.toByteArray(), "UTF-8");
                                onTextMessage(s);
                            }

                        } else if (mMessageOpcode == 2) {

                            // dispatch WS binary message
                            onBinaryMessage(mMessagePayload.toByteArray());

                        } else {

                            // should not arrive here (handled before)
                            throw new Exception("logic error");
                        }

                        // ok, message completed - reset all
                        mInsideMessage = false;
                        mMessagePayload.reset();
                    }
                }

                // reset frame
                mFrameHeader = null;

                // reprocess if more data left
                return mPosition > 0;

            } else {

                // need more data
                return false;
            }
        }
    }


    /**
     * WebSockets handshake reply from server received, default notifies master.
     *
     * @param success Success handshake flag
     */
    private void onHandshake(boolean success) {

        notify(new WebSocketMessage.ServerHandshake(success));
    }


    /**
     * WebSockets close received, default notifies master.
     */
    private void onClose(int code, String reason) {

        notify(new WebSocketMessage.Close(code, reason));
    }


    /**
     * WebSockets ping received, default notifies master.
     *
     * @param payload Ping payload or null.
     */
    private void onPing(byte[] payload) {

        notify(new WebSocketMessage.Ping(payload));
    }


    /**
     * WebSockets pong received, default notifies master.
     *
     * @param payload Pong payload or null.
     */
    private void onPong(byte[] payload) {

        notify(new WebSocketMessage.Pong(payload));
    }


    /**
     * WebSockets text message received, default notifies master.
     * This will only be called when the option receiveTextMessagesRaw
     * HAS NOT been set.
     *
     * @param payload Text message payload as Java String decoded
     *                from raw UTF-8 payload or null (empty payload).
     */
    private void onTextMessage(String payload) {
        notify(new WebSocketMessage.TextMessage(payload));
    }


    /**
     * WebSockets text message received, default notifies master.
     * This will only be called when the option receiveTextMessagesRaw
     * HAS been set.
     *
     * @param payload Text message payload as raw UTF-8 octets or
     *                null (empty payload).
     */
    private void onRawTextMessage(byte[] payload) {

        notify(new WebSocketMessage.RawTextMessage(payload));
    }


    /**
     * WebSockets binary message received, default notifies master.
     *
     * @param payload Binary message payload or null (empty payload).
     */
    private void onBinaryMessage(byte[] payload) {

        notify(new WebSocketMessage.BinaryMessage(payload));
    }

    /**
     * Process WebSockets handshake received from server.
     */
    private boolean processHandshake() throws UnsupportedEncodingException {

        boolean res = false;
        for (int pos = mPosition - 4; pos >= 0; --pos) {
            if (mMessageData[pos] == 0x0d && mMessageData[pos + 1] == 0x0a &&
                    mMessageData[pos + 2] == 0x0d && mMessageData[pos + 3] == 0x0a) {

                // Check HTTP status code
                boolean serverError = false;
                String rawHeaders = new String(Arrays.copyOf(mMessageData, pos + 4), "UTF-8");
                String[] headers = rawHeaders.split("\r\n");
                if (headers[0].startsWith("HTTP")) {
                    Pair<Integer, String> status = parseHttpStatus(headers[0]);
                    Map<String, String> handshakeParams = parseHttpHeaders(Arrays.copyOfRange(headers, 1, headers.length));
                    if (status.first == 302) {
                        String s = handshakeParams.get("Location");
                        if (s != null) {
                            notify(new WebSocketMessage.Redirect(s));
                            return false;
                        } else {
                            notify(new WebSocketMessage.ServerError(status.first, status.second));
                            serverError = true;
                            WatchSdk.reportAnalytics(AnalyticsType.CANNOT_CONNECT,
                                    status.first,status.second + " " +
                                            "handshakeParams does not have location");
                        }
                    }

                    if (status.first >= 400) {
                        // Invalid status code for success connection
                        notify(new WebSocketMessage.ServerError(status.first, status.second));
                        serverError = true;
                        WatchSdk.reportAnalytics(AnalyticsType.CANNOT_CONNECT, status.first,status.second);
                    }
                }
                mMessageData = Arrays.copyOfRange(mMessageData, pos + 4, mMessageData.length + pos + 4);
                mPosition -= pos + 4;

                if (!serverError) {

                    // process further when data after HTTP headers left in buffer
                    res = mPosition > 0;

                    mState = STATE_OPEN;
                } else {
                    res = true;
                    mState = STATE_CLOSED;
                    mStopped = true;
                }
                onHandshake(!serverError);
                break;
            }
        }
        return res;
    }

    private Map<String, String> parseHttpHeaders(String[] httpResponse) {
        Map<String, String> headers = new HashMap<>();
        for (String line : httpResponse) {
            if (line.length() > 0) {
                String[] h = line.split(": ");
                if (h.length == 2) {
                    headers.put(h[0], h[1]);
                }
            }
        }
        return headers;
    }

    private Pair<Integer, String> parseHttpStatus(String statusLine) {
        String[] statusLineParts = statusLine.split(" ");
        int statusCode = Integer.valueOf(statusLineParts[1]);
        StringBuilder statusMessageBuilder = new StringBuilder();
        for (int i = 2; i < statusLineParts.length; i++) {
            statusMessageBuilder.append(statusLineParts[i]);
            statusMessageBuilder.append(" ");
        }
        String statusMessage = statusMessageBuilder.toString().trim();
        return new Pair<>(statusCode, statusMessage);
    }

    /**
     * Consume data buffered in mFrameBuffer.
     */
    private boolean consumeData() throws Exception {

        if (mState == STATE_OPEN || mState == STATE_CLOSING) {

            return processData();

        } else if (mState == STATE_CONNECTING) {

            return processHandshake();

        } else if (mState == STATE_CLOSED) {

            return false;

        } else {
            // should not arrive here
            return false;
        }
    }


    /**
     * Run the background reader thread loop.
     */
    @Override
    public void run() {

        try {
            do {
                // blocking read on socket
                int len = mBufferedStream.read(mMessageData, mPosition, mMessageData.length - mPosition);
                mPosition += len;
                if (len > 0) {

                    // process buffered data
                    while (consumeData()) {

                    }

                } else if (mState == STATE_CLOSED) {

                    mStopped = true;

                } else if (len < 0) {

                    notify(new WebSocketMessage.ConnectionLost());

                    mStopped = true;
                }
            } while (!mStopped);

        } catch (WebSocketException e) {
            notify(new WebSocketMessage.ProtocolViolation(e));
            WatchSdk.reportAnalytics(AnalyticsType.CONNECTION_LOST, CLOSE_PROTOCOL_ERROR, e.getMessage());
            WatchSdk.reportCrash(e,e.getMessage());

        } catch (SocketException e) {
            if (mState != STATE_CLOSED && !mSocket.isClosed()) {
                notify(new WebSocketMessage.ConnectionLost());
                WatchSdk.reportAnalytics(AnalyticsType.CONNECTION_LOST, CLOSE_CONNECTION_LOST, e.getMessage());
            }

        } catch (Exception e) {
            notify(new WebSocketMessage.Error(e));
            WatchSdk.reportAnalytics(AnalyticsType.CONNECTION_LOST, CLOSE_INTERNAL_ERROR, "Exception happen(may be connection aborted)");
            WatchSdk.reportCrash(e,e.getMessage());

        } finally {

            mStopped = true;
        }

    }

}
