package bef.rest;

import java.util.ArrayList;

final class BefrestNotificationBuilder {
    private String title;
    private String body;
    private String icon;
    private ArrayList<BefrestActionNotification> befrestActionNotifications = new ArrayList<>();

    private BefrestNotificationBuilder() {
    }

    public static BefrestNotificationBuilder aBefrestNotification() {
        return new BefrestNotificationBuilder();
    }

    public BefrestNotificationBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public BefrestNotificationBuilder withBody(String body) {
        this.body = body;
        return this;
    }

    public BefrestNotificationBuilder withIcon(String icon) {
        this.icon = icon;
        return this;
    }

    public BefrestNotificationBuilder withBefrestActionNotifications(ArrayList<BefrestActionNotification> befrestActionNotifications) {
        this.befrestActionNotifications = befrestActionNotifications;
        return this;
    }

    public BefrestNotificationBuilder withBefrestActionNotifications(BefrestActionNotification actionNotification) {
        this.befrestActionNotifications.add(actionNotification);
        return this;
    }

    public BefrestNotification build() {
        return new BefrestNotification(title, body, icon, befrestActionNotifications);
    }
}
