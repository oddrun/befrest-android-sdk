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

    private String scheme;
    private String host;
    private int port;
    private String path;
    private String query;
    private String[] subProtocol;
    private WebSocketOptions webSocketOptions;
    private List<NameValuePair> header;
    private String subscribeUrl;

    private static class Loader {
        private static volatile UrlConnection instance = new UrlConnection();
    }

    private UrlConnection() {
        try {
            URI baseUri = new URI(getUrl());
            scheme = baseUri.getScheme();
            if (baseUri.getPort() == -1) {
                if (scheme.equals("ws")) port = 80;
                else port = 443;
            } else port = baseUri.getPort();
            host = baseUri.getHost();
            if (baseUri.getRawPath() == null || baseUri.getRawPath().equals("")) path = "/";
            else path = baseUri.getRawPath();
            if (baseUri.getRawQuery() == null || baseUri.getRawQuery().equals(""))
                query = null;
            else query = baseUri.getRawQuery();
            webSocketOptions = new WebSocketOptions();
            this.header = getHeader();
            subProtocol = null;
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public synchronized static UrlConnection getInstance() {
        return Loader.instance;
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


    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public String[] getSubProtocol() {
        return subProtocol;
    }

    public WebSocketOptions getOptions() {
        return webSocketOptions;
    }

    public List<NameValuePair> getHeaders() {
        return header;
    }
}
