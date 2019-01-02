package bef.rest;

import java.util.ArrayList;
import java.util.Map;

class BefrestNotifications {
    private int id;
    private String title;
    private String body;
    private String icon;
    private String sound;
    private String color;
    private ArrayList<String> body_loc_key;
    private ArrayList<String> body_loc_args;
    private ArrayList<String> title_loc_key;
    private ArrayList<String> title_log_args;
    private String click;
    private String click_payload;

    public String getClick_payload() {
        return click_payload;
    }

    private ArrayList<BefrestActionNotification> clickAction;
    private Map<String, String> data;

    public int getId() {
        return id;
    }

    public String getClick() {
        return click;
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

    public String getSound() {
        return sound;
    }

    public String getColor() {
        return color;
    }

    public ArrayList<String> getBody_loc_key() {
        return body_loc_key;
    }

    public ArrayList<String> getBody_loc_args() {
        return body_loc_args;
    }

    public ArrayList<String> getTitle_loc_key() {
        return title_loc_key;
    }

    public ArrayList<String> getTitle_log_args() {
        return title_log_args;
    }

    public ArrayList<BefrestActionNotification> getClickAction() {
        return clickAction;
    }

    public Map<String, String> getData() {
        return data;
    }

    public static final class BefrestNotificationsBuilder {
        private int id;
        private String title;
        private String body;
        private String icon;
        private String sound;
        private String color;
        private ArrayList<String> body_loc_key;
        private ArrayList<String> body_loc_args;
        private ArrayList<String> title_loc_key;
        private ArrayList<String> title_log_args;
        private String click;
        private String click_payload;
        private ArrayList<BefrestActionNotification> clickAction;
        private Map<String, String> data;

        BefrestNotificationsBuilder() {
        }

        public static BefrestNotificationsBuilder aBefrestNotifications() {
            return new BefrestNotificationsBuilder();
        }

        public BefrestNotificationsBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public BefrestNotificationsBuilder setTitle(String title) {
            this.title = title;
            return this;
        }

        public BefrestNotificationsBuilder setBody(String body) {
            this.body = body;
            return this;
        }

        public BefrestNotificationsBuilder setIcon(String icon) {
            this.icon = icon;
            return this;
        }

        public BefrestNotificationsBuilder setSound(String sound) {
            this.sound = sound;
            return this;
        }

        public BefrestNotificationsBuilder setColor(String color) {
            this.color = color;
            return this;
        }

        public BefrestNotificationsBuilder setBody_loc_key(ArrayList<String> body_loc_key) {
            this.body_loc_key = body_loc_key;
            return this;
        }

        public BefrestNotificationsBuilder setBody_loc_args(ArrayList<String> body_loc_args) {
            this.body_loc_args = body_loc_args;
            return this;
        }

        public BefrestNotificationsBuilder setTitle_loc_key(ArrayList<String> title_loc_key) {
            this.title_loc_key = title_loc_key;
            return this;
        }

        public BefrestNotificationsBuilder setTitle_log_args(ArrayList<String> title_log_args) {
            this.title_log_args = title_log_args;
            return this;
        }

        public BefrestNotificationsBuilder setClick(String click) {
            this.click = click;
            return this;
        }

        public BefrestNotificationsBuilder setClick_payload(String click_payload) {
            this.click_payload = click_payload;
            return this;
        }

        public BefrestNotificationsBuilder setClickAction(ArrayList<BefrestActionNotification> clickAction) {
            this.clickAction = clickAction;
            return this;
        }

        public BefrestNotificationsBuilder setData(Map<String, String> data) {
            this.data = data;
            return this;
        }

        public BefrestNotifications build() {
            BefrestNotifications befrestNotifications = new BefrestNotifications();
            befrestNotifications.clickAction = this.clickAction;
            befrestNotifications.click = this.click;
            befrestNotifications.click_payload = this.click_payload;
            befrestNotifications.sound = this.sound;
            befrestNotifications.body = this.body;
            befrestNotifications.title = this.title;
            befrestNotifications.icon = this.icon;
            befrestNotifications.body_loc_key = this.body_loc_key;
            befrestNotifications.body_loc_args = this.body_loc_args;
            befrestNotifications.data = this.data;
            befrestNotifications.title_loc_key = this.title_loc_key;
            befrestNotifications.title_log_args = this.title_log_args;
            befrestNotifications.id = this.id;
            befrestNotifications.color = this.color;
            return befrestNotifications;
        }
    }
}