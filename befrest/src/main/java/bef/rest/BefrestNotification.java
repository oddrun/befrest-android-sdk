package bef.rest;

import java.util.ArrayList;

public class BefrestNotification {
    private String title;
    private String body;
    private String icon;
    private ArrayList<BefrestActionNotification> befrestActionNotifications = new ArrayList<>();

    public BefrestNotification(String title, String body, String icon, ArrayList<BefrestActionNotification> befrestActionNotifications) {
        this.title = title;
        this.body = body;
        this.icon = icon;
        this.befrestActionNotifications = befrestActionNotifications;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getIcon() {
        return icon;
    }

    public ArrayList<BefrestActionNotification> getBefrestActionNotifications() {
        return befrestActionNotifications;
    }
}
