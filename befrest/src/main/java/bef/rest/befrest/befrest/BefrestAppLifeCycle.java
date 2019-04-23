package bef.rest.befrest.befrest;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;

public class BefrestAppLifeCycle implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    private BefrestAppDelegate befrestAppDelegate;
    private boolean isForeground = false;

    BefrestAppLifeCycle(BefrestAppDelegate befrestAppDelegate) {
        this.befrestAppDelegate = befrestAppDelegate;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        isForeground = true;
        befrestAppDelegate.onAppForeground();
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
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            if (isForeground) {
                isForeground = false;
                befrestAppDelegate.onAppBackground();
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
