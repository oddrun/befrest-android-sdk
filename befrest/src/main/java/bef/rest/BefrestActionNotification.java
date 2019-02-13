package bef.rest;

import java.io.Serializable;

public class BefrestActionNotification implements Serializable {

    private String actionTitle;
    private String actionPayload = "";
    private String actionType;


    public BefrestActionNotification(String actionTitle, String actionPayload, String actionType) {
        this.actionTitle = actionTitle;
        this.actionPayload = actionPayload;
        this.actionType = actionType;
    }


    public BefrestActionNotification(String actionTitle, String actionType) {
        this.actionTitle = actionTitle;
        this.actionType = actionType;
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
