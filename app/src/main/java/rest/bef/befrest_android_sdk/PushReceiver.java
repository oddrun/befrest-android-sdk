package rest.bef.befrest_android_sdk;

import bef.rest.BefrestNotifications;
import bef.rest.notifreceicer;

public class PushReceiver extends notifreceicer {


    @Override
    public void b(BefrestNotifications befrestNotifications) {
        befrestNotifications.setTitle("helloww");
        super.b(befrestNotifications);
    }
}