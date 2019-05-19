package bef.rest.befrest.websocket;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import bef.rest.befrest.Befrest;
import bef.rest.befrest.autobahnLibrary.WebSocketMessage;
import bef.rest.befrest.autobahnLibrary.WebSocketOptions;
import bef.rest.befrest.autobahnLibrary.WebSocketReader;
import bef.rest.befrest.autobahnLibrary.WebSocketWriter;
import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.UrlConnection;

public class SocketHelper {
    private static final String TAG = "SocketHelper";
    private Socket socket;
    private WebSocketReader reader;
    private WebSocketWriter writer;
    private HandlerThread writerThread;
    private Handler handler;
    private UrlConnection urlConnection;

    public SocketHelper(Handler handler) {
        this.handler = handler;
    }

    public void createSocket() throws IOException {
        this.urlConnection = UrlConnection.getInstance();
        String host = urlConnection.getHost();
        int port = urlConnection.getPort();
        WebSocketOptions webSocketOptions = urlConnection.getOptions();
        if (urlConnection.getScheme().equals("wss")) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket secSoc = (SSLSocket) factory.createSocket();
            secSoc.setUseClientMode(true);
            secSoc.setKeepAlive(true);
            secSoc.connect(new InetSocketAddress(host, port), webSocketOptions.getSocketConnectTimeout());
            secSoc.setTcpNoDelay(webSocketOptions.getTcpNoDelay());
            secSoc.addHandshakeCompletedListener(event -> {
            });
            socket = secSoc;
        } else {
            socket = new Socket(host, port);
            socket.setSendBufferSize(2 * 1024 * 1024);
        }

        createWriter();
        createReader();

    }

    private void createWriter() throws IOException {
        writerThread = new HandlerThread("WriterThread");
        writerThread.start();
        if (socket != null)
            writer = new WebSocketWriter(writerThread.getLooper(), handler, socket, urlConnection.getOptions());
        else {
            BefrestLog.d(TAG, "socket is null and Writer Thread cant create");
        }
    }

    private void createReader() throws IOException {
        if (socket != null) {
            reader = new WebSocketReader(handler, socket, urlConnection.getOptions(), "ReaderThread");
            reader.start();
        } else {
            BefrestLog.d(TAG, "Socket is null and Reader Thread cant create");
        }
    }

    public void startWebSocketHandshake() {
        BefrestLog.i(TAG, "startWebSocketHandShake");
        WebSocketMessage.ClientHandshake hs = new WebSocketMessage.ClientHandshake(
                urlConnection.getHost() + ":" + urlConnection.getPort());
        hs.mPath = urlConnection.getPath();
        hs.mQuery = urlConnection.getQuery();
        hs.mSubProtocols = urlConnection.getSubProtocol();
        hs.mHeaderList = urlConnection.getHeaders();
        writeOnWebSocket(hs);
    }

    public void writeOnWebSocket(WebSocketMessage.Message message) {
        if (writer != null){
            writer.flush();
            writer.forward(message);
        }
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

    public boolean isisReaderAlive() {
        return reader.isAlive();
    }

    public void fillNull() {
        reader = null;
        writer = null;
        writerThread = null;
    }

    public boolean isSocketHelperValid() {
        return reader != null && writer != null && writerThread != null;
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
            socket = null;
        }
    }

}
