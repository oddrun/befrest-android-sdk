package bef.rest.befrest.autobahnLibrary;


import static bef.rest.befrest.utils.BefrestPreferences.PREF_HANDSHAKE_TIMEOUT;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;

/**
 * WebSockets connection options. This can be supplied to WebSocketConnection in connect().
 * Note that the latter copies the options provided to connect(), so any change after
 * connect will have no effect.
 */
public class WebSocketOptions {

    private int mMaxFramePayloadSize;
    private int mMaxMessagePayloadSize;
    private boolean mReceiveTextMessagesRaw;
    private boolean mTcpNoDelay;
    private int mSocketConnectTimeout;
    private boolean mValidateIncomingUtf8;
    private boolean mMaskClientFrames;


    /**
     * Construct default options.
     */
    public WebSocketOptions() {
        mMaxFramePayloadSize = 512 * 1024; // much more than 32k
        mMaxMessagePayloadSize = 128 * 1024; // much more than 32k
        mReceiveTextMessagesRaw = false;
        mTcpNoDelay = true;
        mSocketConnectTimeout = getHandShakeTimeOut();
        mValidateIncomingUtf8 = true;
        mMaskClientFrames = true;
    }

    private int getHandShakeTimeOut() {
        if (getPrefs() != null)
            return getPrefs().getInt(PREF_HANDSHAKE_TIMEOUT, 7_000);
        return 7_000;
    }

    /**
     * When true, WebSockets text messages are provided as
     * verified, but non-decoded UTF-8 in byte arrays.
     *
     * @return True, iff option is enabled.
     */
    boolean getReceiveTextMessagesRaw() {
        return mReceiveTextMessagesRaw;
    }

    /**
     * Get maximum frame payload size that will be accepted
     * when receiving.
     *
     * @return Maximum size in octets for frame payload.
     */
    int getMaxFramePayloadSize() {
        return mMaxFramePayloadSize;
    }


    /**
     * Get maximum message payload size (after reassembly of fragmented
     * messages) that will be accepted when receiving.
     *
     * @return Maximum size in octets for message payload.
     */
    int getMaxMessagePayloadSize() {
        return mMaxMessagePayloadSize;
    }


    /**
     * Get TCP No-Delay ("Nagle") for TCP connection.
     *
     * @return True, iff TCP No-Delay is enabled.
     */
    public boolean getTcpNoDelay() {
        return mTcpNoDelay;
    }


    /**
     * Get socket connect timeout.
     *
     * @return Socket receive timeout in ms.
     */
    public int getSocketConnectTimeout() {
        return mSocketConnectTimeout;
    }


    /**
     * Get UTF-8 validation option.
     *
     * @return True, iff incoming UTF-8 is validated.
     */
    boolean getValidateIncomingUtf8() {
        return mValidateIncomingUtf8;
    }

    /**
     * Get mask client frames option.
     *
     * @return True, iff client-to-server frames are masked.
     */
    boolean getMaskClientFrames() {
        return mMaskClientFrames;
    }

}
