package bef.rest;

import android.app.Application;
import android.util.Log;

public abstract class BefrestApp extends Application implements BefrestAppDelegate {

    @Override
    public void onCreate() {
        super.onCreate();
        BefrestAppLifeCycle lifeCycleHandler = new BefrestAppLifeCycle(this);
        registerLifecycleHandler(lifeCycleHandler);
    }

    @Override
    public void onAppForeGrounded() {
        Log.i("AAAAAAAAB", "onAppForeGrounded: ");
        onAppForeground();
    }

    public abstract void onAppForeground();

    private void registerLifecycleHandler(BefrestAppLifeCycle appLifecycleHandler) {
        registerActivityLifecycleCallbacks(appLifecycleHandler);
        registerComponentCallbacks(appLifecycleHandler);
    }
}
