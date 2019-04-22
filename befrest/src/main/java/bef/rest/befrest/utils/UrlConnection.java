package bef.rest.befrest.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import bef.rest.befrest.clientData.ClientData;
import bef.rest.befrest.websocket.WebSocketOptions;

import static bef.rest.befrest.utils.SDKConst.API_VERSION;
import static bef.rest.befrest.utils.SDKConst.SDK_VERSION;

public class UrlConnection {
    private static UrlConnection instance;
    private String mWsScheme;
    private String mWsHost;
    private int mWsPort;
    private String mWsPath;
    private String mWsQuery;
    private String[] mWsSubprotocols;
    private WebSocketOptions webSocketOptions;
    private List<NameValuePair> header;
    private String subscribeUrl;

    private UrlConnection() {
        try {
            URI baseUri = new URI(getUrl());
            mWsScheme = baseUri.getScheme();
            if (baseUri.getPort() == -1) {
                if (mWsScheme.equals("ws")) mWsPort = 80;
                else mWsPort = 443;
            } else mWsPort = baseUri.getPort();
            mWsHost = baseUri.getHost();
            if (baseUri.getRawPath() == null || baseUri.getRawPath().equals("")) mWsPath = "/";
            else mWsPath = baseUri.getRawPath();
            if (baseUri.getRawQuery() == null || baseUri.getRawQuery().equals(""))
                mWsQuery = null;
            else mWsQuery = baseUri.getRawQuery();
            webSocketOptions = new WebSocketOptions();
            this.header = getHeader();
            mWsSubprotocols = null;
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public synchronized static UrlConnection getInstance() {
        if (instance == null) {
            instance = new UrlConnection();
        }
        return instance;
    }

    private List<NameValuePair> getHeader() {
        if (header == null) {
            header = new ArrayList<>();
            header.add(ClientData.getInstance().getAuthHeader());
            String topic = ClientData.getInstance().getTopics();
            if (topic.length() > 0)
                header.add(ClientData.getInstance().getTopic());
        }
        return header;
    }

    private String getUrl() {
        if (subscribeUrl == null)
            subscribeUrl = String.format(Locale.US, "wss://gw.bef.rest/xapi/%d/subscribe/%d/%s/%d",
                    API_VERSION, ClientData.getInstance().getUId()
                    , ClientData.getInstance().getChId(), SDK_VERSION);
        return subscribeUrl;
    }


    public String getmWsScheme() {
        return mWsScheme;
    }

    public String getmWsHost() {
        return mWsHost;
    }

    public int getmWsPort() {
        return mWsPort;
    }

    public String getmWsPath() {
        return mWsPath;
    }

    public String getmWsQuery() {
        return mWsQuery;
    }

    public String[] getmWsSubprotocols() {
        return mWsSubprotocols;
    }

    public WebSocketOptions getOptions() {
        return webSocketOptions;
    }

    public List<NameValuePair> getmWsHeaders() {
        return header;
    }
}
