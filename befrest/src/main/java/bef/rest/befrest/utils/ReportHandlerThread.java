package bef.rest.befrest.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class ReportHandlerThread extends HandlerThread {
    private Handler handler;

    private static class Loader {
        static volatile ReportHandlerThread instance = new ReportHandlerThread("ReportManager");
    }

    public static ReportHandlerThread getInstance() {
        return Loader.instance;
    }

    private ReportHandlerThread(String name) {
        super(name);
        start();
        handler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    super.handleMessage(msg);
                    //TODO create queue and set Data to Server
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        };
    }

    public void forward(Object s) {
        Message message = Message.obtain();
        handler.sendMessage(message);

    }
}
