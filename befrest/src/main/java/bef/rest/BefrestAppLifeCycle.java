package bef.rest;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;

public class BefrestAppLifeCycle implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    BefrestAppDelegate befrestLifeCycle;
    private boolean isAppForeground = false;

    public BefrestAppLifeCycle(BefrestAppDelegate befrestLifeCycle) {
        this.befrestLifeCycle = befrestLifeCycle;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (!isAppForeground) {
            isAppForeground = true;
            befrestLifeCycle.onAppForeGrounded();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    @Override
    public void onTrimMemory(int level) {
        if (isAppForeground) {
            if (level == TRIM_MEMORY_UI_HIDDEN) {
                isAppForeground = false;
                befrestLifeCycle.onAppBackground();

            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

    }

    @Override
    public void onLowMemory() {

    }
}
