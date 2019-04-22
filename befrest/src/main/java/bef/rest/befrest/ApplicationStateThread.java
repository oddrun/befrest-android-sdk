package bef.rest.befrest;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import bef.rest.befrest.befrest.Befrest;
import bef.rest.befrest.befrest.BefrestAppDelegate;

import static bef.rest.befrest.utils.Util.isAppOnForeground;

public class ApplicationStateThread extends Handler {

    private BefrestAppDelegate befrestAppDelegate;
    private boolean check;

    public ApplicationStateThread(Looper looper, BefrestAppDelegate t) {
        super(looper);
        this.befrestAppDelegate = t;
    }

    public void forward(boolean check) {
        Message message = new Message();
        message.obj = check;
        sendMessage(message);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        if (msg.obj instanceof Boolean) {
            check = (boolean) msg.obj;
            try {
                notifyLoop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyLoop() throws InterruptedException {
        while (check) {
            if (Befrest.getInstance().isAppInForeground() != isAppOnForeground()) {
                Befrest.getInstance().setAppInForeground(isAppOnForeground());
                if (Befrest.getInstance().isAppInForeground()) {
                    befrestAppDelegate.onAppForeground();
                } else
                    befrestAppDelegate.onAppBackground();
            }
            Thread.sleep(3000);
        }
    }

}
