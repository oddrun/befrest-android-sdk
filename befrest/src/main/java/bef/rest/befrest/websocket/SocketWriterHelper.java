package bef.rest.befrest.websocket;

import android.os.Handler;
import android.os.HandlerThread;
import java.io.IOException;
import java.net.Socket;

import bef.rest.befrest.utils.UrlConnectionWrapper;

public class SocketWriterHelper {
    private WebSocketWriter webSocketWriter;
    private HandlerThread mWriterThread;
    private UrlConnectionWrapper wrapper;
    private Handler handler;

    public SocketWriterHelper(UrlConnectionWrapper wrapper, Handler handler) {
        this.wrapper = wrapper;
        this.handler = handler;
    }

    public void createSocketWriter(Socket socket) throws IOException {
        mWriterThread = new HandlerThread("WebSocketWriter");
        mWriterThread.start();
        webSocketWriter = new WebSocketWriter(mWriterThread.getLooper(), handler, socket, wrapper.getOptions());

    }

    public void startWebSocketHandshake() {
        WebSocketMessage.ClientHandshake hs = new WebSocketMessage.ClientHandshake(
                wrapper.getmWsHost() + ":" + wrapper.getmWsPort());
        hs.mPath = wrapper.getmWsPath();
        hs.mQuery = wrapper.getmWsQuery();
        hs.mSubprotocols = wrapper.getmWsSubprotocols();
        hs.mHeaderList = wrapper.getmWsHeaders();
       writeOnWebSocket(hs);
    }

    public void writeOnWebSocket(WebSocketMessage.Message message) {
            webSocketWriter.forward(message);
    }

    public void free() {
        webSocketWriter.forward(new WebSocketMessage.Quit());
    }

    public void join() throws InterruptedException {
        mWriterThread.join(1000);
    }
}
