package bef.rest.befrest.utils;

import android.text.TextUtils;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

import bef.rest.befrest.befrest.Befrest;

public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static String BASE_URL = "";
    private Executor executor;
    private int connectionTimeOut;

    private static class Loader {
        private static volatile NetworkManager instance = new NetworkManager();
    }

    public static NetworkManager getInstance() {
        return Loader.instance;
    }

    private NetworkManager() {
        executor = Executors.newSingleThreadExecutor();
        connectionTimeOut = 10_000;
    }

    public HttpURLConnection openConnection(String method, String url,
                                            Map<String, String> header, Map<String, Object> params) {
        BefrestLog.d(TAG, "Request to open Connection with method : " + method + " , to url : " + url
                + " , with header : " + header + " and params : " + params);
        HttpURLConnection connection;
        try {
            CookieManager cookieManager = new CookieManager();
            if (header == null)
                header = new HashMap<>();
            if (!url.startsWith("http") && !url.startsWith("https"))
                return null;
            String cookies = getCookiesHeaderForUrl(cookieManager, url);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5 * 1000);
            connection.setReadTimeout(NetworkManager.getInstance().connectionTimeOut);
            connection.setInstanceFollowRedirects(true); //does not follow redirects from a protocol to another (e.g. http to https and vice versa)
            connection.setRequestMethod(method);
            if (cookies != null && !TextUtils.isEmpty(cookies))
                connection.setRequestProperty("Cookie", cookies);
            for (Map.Entry<String, String> entry : header.entrySet())
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            if ("POST".equals(method) && params != null && !params.isEmpty()) {
                connection.setDoOutput(true);
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(getQuery(params).getBytes());
                outputStream.flush();
                outputStream.close();
            }
            if ("PUT".equals(method) && params != null) {
                connection.setDoInput(true);
                OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
                osw.write(new Gson().toJson(params));
                osw.flush();
                osw.close();
            }

            if (connection.getHeaderFields() != null)
                addCookiesToCookieManager(cookieManager, url, connection.getHeaderFields().get("Set-Cookie"));
            return connection;
        } catch (SocketException | SocketTimeoutException | SSLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getCookiesHeaderForUrl(CookieManager cookieManager, String url) {
        StringBuilder s = new StringBuilder();
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (uri != null) {
            for (HttpCookie cookie : cookieManager.getCookieStore().get(uri))
                s.append(cookie.getName()).append("=").append(cookie.getValue()).append(";");
        }
        if (TextUtils.isEmpty(s.toString()))
            return null;
        else
            return s.substring(0, s.length() - 1);
    }

    private String getQuery(Map<String, Object> params) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&");
            try {
                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return result.toString();
    }

    private void addCookiesToCookieManager(CookieManager cookieManager, String url,
                                           List<String> cookies) {
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (cookies != null)
            for (String cookie : cookies)
                try {
                    cookieManager.getCookieStore().add(uri, HttpCookie.parse(cookie).get(0));
                } catch (Exception e) {
                    e.printStackTrace();
                }
    }

}
