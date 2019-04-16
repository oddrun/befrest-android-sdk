package bef.rest.befrest.websocket;

import android.os.Handler;

import java.io.IOException;
import java.net.Socket;

public class SocketReaderHelper {
    private WebSocketReader reader;
    private WebSocketOptions options;

    private Handler handler;

    public SocketReaderHelper(WebSocketOptions options, Handler handler) {
        this.options = options;
        this.handler = handler;
    }


    public void createReader(Socket socket) {
        try {
            reader = new WebSocketReader(handler, socket, options, "WebSocketReader");
        } catch (IOException e) {
            e.printStackTrace();
        }
        reader.start();
    }


    public void free() {
        reader.quit();
    }


    public void join() throws InterruptedException {
        reader.join(1000);
    }
}
