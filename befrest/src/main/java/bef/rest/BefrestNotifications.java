package bef.rest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class BefrestNotifications implements Serializable {
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
    private HashMap<String, String> data;
    private String smallIcon;
    private String group;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    String getSmallIcon() {
        return smallIcon;
    }

    void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    String getSound() {
        return sound;
    }

    void setSound(String sound) {
        this.sound = sound;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public ArrayList<String> getBody_loc_key() {
        return body_loc_key;
    }

    public void setBody_loc_key(ArrayList<String> body_loc_key) {
        this.body_loc_key = body_loc_key;
    }

    public ArrayList<String> getBody_loc_args() {
        return body_loc_args;
    }

    public void setBody_loc_args(ArrayList<String> body_loc_args) {
        this.body_loc_args = body_loc_args;
    }

    public ArrayList<String> getTitle_loc_key() {
        return title_loc_key;
    }

    public void setTitle_loc_key(ArrayList<String> title_loc_key) {
        this.title_loc_key = title_loc_key;
    }

    public ArrayList<String> getTitle_log_args() {
        return title_log_args;
    }

    public void setTitle_log_args(ArrayList<String> title_log_args) {
        this.title_log_args = title_log_args;
    }

    String getClick() {
        return click;
    }

    void setClick(String click) {
        this.click = click;
    }

    String getClick_payload() {
        return click_payload;
    }

    void setClick_payload(String click_payload) {
        this.click_payload = click_payload;
    }

    ArrayList<BefrestActionNotification> getClickAction() {
        return clickAction;
    }

    void setClickAction(ArrayList<BefrestActionNotification> clickAction) {
        this.clickAction = clickAction;
    }

    public HashMap<String, String> getData() {
        return data;
    }

    public void setData(HashMap<String, String> data) {
        this.data = data;
    }
}