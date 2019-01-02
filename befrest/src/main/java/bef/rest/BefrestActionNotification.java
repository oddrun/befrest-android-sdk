package bef.rest;

import org.json.JSONObject;

public class BefrestActionNotification {

    private String actionTitle;
    private String actionPayload = "";
    private String actionType;
    private JSONObject jsonObject;

    public BefrestActionNotification(String actionTitle, String actionPayload, String actionType) {
        this.actionTitle = actionTitle;
        this.actionPayload = actionPayload;
        this.actionType = actionType;
    }

    public BefrestActionNotification(String actionTitle, JSONObject jsonObject, String actionType) {
        this.actionTitle = actionTitle;
        this.actionType = actionType;
        this.jsonObject = jsonObject;
    }

    public BefrestActionNotification(String actionTitle, String actionType) {
        this.actionTitle = actionTitle;
        this.actionType = actionType;
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public String getActionTitle() {
        return actionTitle;
    }

    public String getActionPayload() {
        return actionPayload;
    }

    public String getActionType() {
        return actionType;
    }
}
