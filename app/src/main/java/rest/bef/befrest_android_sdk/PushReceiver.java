package rest.bef.befrest_android_sdk;

import bef.rest.BefrestNotifications;
import bef.rest.NotificationReceiver;

public class PushReceiver extends NotificationReceiver {


    @Override
    public void beforeShowCallBack(BefrestNotifications befrestNotifications) {
        befrestNotifications.setTitle("Hello");
        super.beforeShowCallBack(befrestNotifications);
    }
}