package bef.rest.befrest.websocket;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.UrlConnection;

public class SocketHelper {
    private static final String TAG = "SocketHelper";
    private Socket socket;
    private WebSocketReader reader;
    private WebSocketWriter writer;
    private HandlerThread writerThread;
    private Handler handler;
    private UrlConnection wrapper;

    public SocketHelper(Handler handler) {
        this.handler = handler;
        this.wrapper = UrlConnection.getInstance();
    }

    public void createSocket() throws IOException {
        String host = wrapper.getHost();
        int port = wrapper.getPort();
        WebSocketOptions webSocketOptions = wrapper.getOptions();
        if (wrapper.getScheme().equals("wss")) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket secSoc = (SSLSocket) factory.createSocket();
            secSoc.setUseClientMode(true);
            secSoc.connect(new InetSocketAddress(host, port), webSocketOptions.getSocketConnectTimeout());
            secSoc.setTcpNoDelay(webSocketOptions.getTcpNoDelay());
            secSoc.addHandshakeCompletedListener(event -> {
            });
            socket = secSoc;
        } else
            socket = new Socket(host, port);
        createWriter();
        createReader();
    }

    private void createWriter() throws IOException {
        writerThread = new HandlerThread("WriterThread");
        writerThread.start();
        if (socket != null)
            writer = new WebSocketWriter(writerThread.getLooper(), handler, socket, wrapper.getOptions());
        else {
            createSocket();
            BefrestLog.d(TAG, "socket is null and Writer Thread cant create");
        }
    }

    private void createReader() throws IOException {
        if (socket != null) {
            reader = new WebSocketReader(handler, socket, wrapper.getOptions(), "ReaderThread");
            reader.start();
        } else {
            BefrestLog.d(TAG, "Socket is null and Reader Thread cant create");
        }
    }

    public void startWebSocketHandshake() {
        BefrestLog.i(TAG, "startWebSocketHandShake");
        WebSocketMessage.ClientHandshake hs = new WebSocketMessage.ClientHandshake(
                wrapper.getHost() + ":" + wrapper.getPort());
        hs.mPath = wrapper.getPath();
        hs.mQuery = wrapper.getQuery();
        hs.mSubProtocols = wrapper.getSubProtocol();
        hs.mHeaderList = wrapper.getHeaders();
        writeOnWebSocket(hs);
    }

    public void writeOnWebSocket(WebSocketMessage.Message message) {
        if (writer != null)
            writer.forward(message);
        else
            BefrestLog.d(TAG, "Writer is null");
    }

    public void freeWriter() {
        writeOnWebSocket(new WebSocketMessage.Quit());
    }

    public void joinWriter() throws InterruptedException {
        if (writerThread != null)
            writerThread.join(1000);
    }

    public void freeReader() {
        if (reader != null)
            reader.quit();
    }


    public void joinReader() throws InterruptedException {
        if (reader != null)
            reader.join(1000);
    }

    public boolean isSocketConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void freeSocket() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

}
