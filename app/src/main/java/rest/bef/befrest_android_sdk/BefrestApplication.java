package rest.bef.befrest_android_sdk;

import android.app.Application;
import android.util.Log;

import bef.rest.BefrestFactory;

public class BefrestApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("BEFRESTAPP", "------------------------------onCreate:------------------------------");
        BefrestFactory.getInstance(this)
                .init(12013, "Va7Q92hJy-VkHSJol1yeUA", "chOne2");
//                 .init(12013, "CmLxC9DVztyi-pxi54xZKA", "quickstartchannel");

    }
}
