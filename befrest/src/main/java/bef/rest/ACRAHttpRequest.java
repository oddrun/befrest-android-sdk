/*
 * This class was copied from this Stackoverflow Q&A:
 * http://stackoverflow.com/questions/2253061/secure-http-post-in-android/2253280#2253280
 * Thanks go to MattC!  
 */
package bef.rest;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

final class ACRAHttpRequest {
    private static final String TAG = BefLog.TAG_PREF + "ACRAHttpRequest";

    public enum Method {
        POST, PUT
    }

    /**
     * Type of report data encoding, currently supports Html Form encoding and
     * JSON.
     */
    public enum Type {
        /**
         * Send data as a www form encoded list of key/values.
         *
         * @see <a href="http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4">Form content types</a>
         */
        FORM {
            @Override
            public String getContentType() {
                return "application/x-www-form-urlencoded";
            }
        },
        /**
         * Send data as a structured JSON tree.
         */
        JSON {
            @Override
            public String getContentType() {
                return "application/json";
            }
        };

        public abstract String getContentType();
    }

    //    private final ACRAConfiguration config;
    private int connectionTimeOut = 3000;
    private int socketTimeOut = 3000;
    private Map<String, String> headers;

    public void setConnectionTimeOut(int connectionTimeOut) {
        this.connectionTimeOut = connectionTimeOut;
    }

    public void setSocketTimeOut(int socketTimeOut) {
        this.socketTimeOut = socketTimeOut;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }


    /**
     * Posts to a URL.
     *
     * @param url     URL to which to post.
     * @param content Map of parameters to post to a URL.
     * @throws IOException if the data cannot be posted.
     */
    public void send(URL url, Method method, String content, Type type) throws IOException {

        final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

        // Configure SSL
        if (urlConnection instanceof HttpsURLConnection) {
            try {
                final HttpsURLConnection httpsUrlConnection = (HttpsURLConnection) urlConnection;

                final String algorithm = TrustManagerFactory.getDefaultAlgorithm();
                final TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);

//                tmf.init(config.keyStore());

                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);

                httpsUrlConnection.setSSLSocketFactory(sslContext.getSocketFactory());
            } catch (GeneralSecurityException e) {
                BefLog.e(TAG, "Could not configure SSL for ACRA request to " + url, e);
            }
        }

        urlConnection.setRequestProperty("Authorization", "Basic YmVmcmVzdC1yZXBvcnRlcjpiZWZyZXN0LXJlcG9ydGVyLXBhc3N3b3Jk");

        urlConnection.setConnectTimeout(connectionTimeOut);
        urlConnection.setReadTimeout(socketTimeOut);

        // Set Headers
        urlConnection.setRequestProperty("User-Agent", "Android");
        urlConnection.setRequestProperty("Accept",
                "text/html,application/xml,application/json,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        urlConnection.setRequestProperty("Content-Type", type.getContentType());

        if (headers != null) {
            for (final Map.Entry<String, String> header : headers.entrySet()) {
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        final byte[] contentAsBytes = content.getBytes("UTF-8");

        // write output - see http://developer.android.com/reference/java/net/HttpURLConnection.html
        urlConnection.setRequestMethod(method.name());
        urlConnection.setDoOutput(true);
        urlConnection.setFixedLengthStreamingMode(contentAsBytes.length);

        // Disable ConnectionPooling because otherwise OkHttp ConnectionPool will try to start a Thread on #connect
        System.setProperty("http.keepAlive", "false");

        urlConnection.connect();

        final OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
        outputStream.write(contentAsBytes);
        outputStream.flush();
        outputStream.close();

        BefLog.d(TAG, "Sending request to " + url);
        BefLog.d(TAG, "Http " + method.name() + " content : ");
        BefLog.d(TAG, content);

        final int responseCode = urlConnection.getResponseCode();
        BefLog.d(TAG, "Request response : " + responseCode + " : " + urlConnection.getResponseMessage());
        if ((responseCode >= 200) && (responseCode < 300)) {
            // All is good
            BefLog.i(TAG, "Request received by server");
        } else if (responseCode == 403) {
            // 403 is an explicit data validation refusal from the server. The request must not be repeated. Discard it.
            BefLog.w(TAG, "Data validation error on server - request will be discarded");
        } else if (responseCode == 409) {
            // 409 means that the report has been received already. So we can discard it.
            BefLog.w(TAG, "Server has already received this post - request will be discarded");
        } else if ((responseCode >= 400) && (responseCode < 600)) {
            BefLog.w(TAG, "Could not send ACRA Post responseCode=" + responseCode + " message=" + urlConnection.getResponseMessage());
            throw new IOException("Host returned error code " + responseCode);
        } else {
            BefLog.w(TAG, "Could not send ACRA Post - request will be discarded responseCode=" + responseCode + " message=" + urlConnection.getResponseMessage());
        }

        urlConnection.disconnect();
    }

    /**
     * Converts a Map of parameters into a URL encoded Sting.
     *
     * @param parameters Map of parameters to convert.
     * @return URL encoded String representing the parameters.
     * @throws UnsupportedEncodingException if one of the parameters couldn't be converted to UTF-8.
     */
    public static String getParamsAsFormString(Map<?, ?> parameters) throws UnsupportedEncodingException {

        final StringBuilder dataBfr = new StringBuilder();
        for (final Map.Entry<?, ?> entry : parameters.entrySet()) {
            if (dataBfr.length() != 0) {
                dataBfr.append('&');
            }
            final Object preliminaryValue = entry.getValue();
            final Object value = (preliminaryValue == null) ? "" : preliminaryValue;
            dataBfr.append(URLEncoder.encode(entry.getKey().toString(), "UTF-8"));
            dataBfr.append('=');
            dataBfr.append(URLEncoder.encode(value.toString(), "UTF-8"));
        }

        return dataBfr.toString();
    }
}
