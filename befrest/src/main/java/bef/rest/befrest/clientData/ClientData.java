package bef.rest.befrest.clientData;

import bef.rest.befrest.utils.BefrestLog;
import bef.rest.befrest.utils.NameValuePair;

import static bef.rest.befrest.utils.BefrestPreferences.PREF_CH_ID;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_TOPICS;
import static bef.rest.befrest.utils.BefrestPreferences.PREF_U_ID;
import static bef.rest.befrest.utils.BefrestPreferences.getPrefs;
import static bef.rest.befrest.utils.BefrestPreferences.saveInt;
import static bef.rest.befrest.utils.BefrestPreferences.saveString;
import static bef.rest.befrest.utils.SDKConst.LOG_LEVEL_VERBOSE;

public class ClientData {
    private String TAG = ClientData.class.getName();
    private int uId;
    private String chId;
    private String authToken;
    private String topics;
    private NameValuePair authHeader, topicHeader;
    private int logLevel = LOG_LEVEL_VERBOSE;

    private ClientData() {
        topics = getPrefs().getString(PREF_TOPICS, null);
        if (topics == null)
            topics = "";
    }

    private static class Loader {
        private static volatile ClientData instance = new ClientData();
    }

    public static ClientData getInstance() {
        return Loader.instance;
    }

    public NameValuePair getAuthHeader() {
        if (authHeader == null) {
            authHeader = new NameValuePair("X-BF-AUTH", authToken);
        }
        return authHeader;
    }

    public void setData(int uId, String chId, String authToken) {
        setUId(uId);
        setChId(chId);
        setAuthToken(authToken);
    }

    private void setUId(int uId) {
        this.uId = uId;
        saveInt(PREF_U_ID, uId);
    }

    private void setChId(String chId) {
        this.chId = chId;
        saveString(PREF_CH_ID, chId);
    }

    private void setAuthToken(String authToken) {
        this.authToken = authToken;
        saveString(PREF_CH_ID, chId);
    }

    public void setTopics(String topic) {
        String s = this.topics;
        if (s.length() > 0) {
            s += "-";
        }
        s += topic;
        updateTopic(s);
        BefrestLog.i(TAG, "Topic : " + topic + " add");
    }

    public int getUId() {
        return uId;
    }

    public String getChId() {
        return chId;
    }

    public String getTopics() {
        return topics;
    }

    public NameValuePair getTopic() {
        if (topicHeader == null) {
            topicHeader = new NameValuePair("X-BF-TOPICS", topics);
        }
        return topicHeader;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public void updateTopic(String topics) {
        this.topics = topics;
        saveString(PREF_TOPICS, this.topics);
        BefrestLog.i(TAG, "updatedTopic: is " + this.topics);
    }


}
