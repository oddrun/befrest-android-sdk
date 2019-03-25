package rest.bef.befrest_android_sdk;

import android.app.Application;

import bef.rest.BefrestFactory;

public class BefrestApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        BefrestFactory.getInstance(this)
                .init(12013, new BefrestAuth().generateSubscriptionAuth("ch02", 2), "ch02").start();

    }
}
