package org.telegram.othergram.network;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.othergram.OtherGramConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTPS client for the OtherGram backend. All calls run on a dedicated thread and
 * report back on the UI thread. Nothing here is required for Telegram itself to work.
 */
public class OtherGramApi {

    /**
     * Backend address. Empty until the Worker is deployed; while empty no request is ever made
     * and OtherGram-only features (badges, banners, admin panel) stay inactive.
     */
    public static final String BASE_URL = "";

    private static final String KEY_TOKEN = "server_token";
    private static final String KEY_USER_ID = "server_user_id";
    private static final String KEY_IS_ADMIN = "server_is_admin";
    private static final int TIMEOUT_MS = 15000;

    private static final DispatchQueue queue = new DispatchQueue("othergramApi");

    public interface Callback {
        /** @param status HTTP status, or 0 when the request did not reach the server */
        void onResult(int status, JSONObject response);
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(BASE_URL);
    }

    public static boolean isLoggedIn() {
        return !TextUtils.isEmpty(OtherGramConfig.getString(KEY_TOKEN, null));
    }

    public static long getLoggedInUserId() {
        return Long.parseLong(OtherGramConfig.getString(KEY_USER_ID, "0"));
    }

    /** Admin flag as last reported by the server; every admin action is checked by the server again. */
    public static boolean isAdmin() {
        return OtherGramConfig.getBoolean(KEY_IS_ADMIN, false);
    }

    static void saveSession(String token, long userId, boolean admin) {
        OtherGramConfig.putString(KEY_TOKEN, token);
        OtherGramConfig.putString(KEY_USER_ID, Long.toString(userId));
        OtherGramConfig.putBoolean(KEY_IS_ADMIN, admin);
    }

    static void updateAdmin(boolean admin) {
        if (admin != isAdmin()) {
            OtherGramConfig.putBoolean(KEY_IS_ADMIN, admin);
        }
    }

    /** Re-reads own account state (admin flag) from the server. */
    public static void refreshMe(Runnable onDone) {
        if (!isLoggedIn()) {
            return;
        }
        getJson("/v1/me", (status, response) -> {
            if (status == 200 && response != null) {
                updateAdmin(response.optBoolean("is_admin"));
            }
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    public static void logout() {
        OtherGramConfig.remove(KEY_TOKEN);
        OtherGramConfig.remove(KEY_USER_ID);
        OtherGramConfig.remove(KEY_IS_ADMIN);
    }

    public static void getJson(String path, Callback callback) {
        request("GET", path, null, null, callback);
    }

    public static void sendJson(String method, String path, JSONObject body, Callback callback) {
        request(method, path, body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : null, "application/json", callback);
    }

    public static void sendBytes(String method, String path, byte[] body, String contentType, Callback callback) {
        request(method, path, body, contentType, callback);
    }

    private static void request(String method, String path, byte[] body, String contentType, Callback callback) {
        if (!isConfigured()) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(0, null));
            }
            return;
        }
        final String token = OtherGramConfig.getString(KEY_TOKEN, null);
        queue.postRunnable(() -> {
            int status = 0;
            JSONObject response = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                if (!TextUtils.isEmpty(token)) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", contentType);
                    connection.setFixedLengthStreamingMode(body.length);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body);
                    }
                }
                status = connection.getResponseCode();
                final InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                if (in != null) {
                    final String text = readAll(in);
                    if (!TextUtils.isEmpty(text)) {
                        response = new JSONObject(text);
                    }
                }
                if (status == 401 && !TextUtils.isEmpty(token)) {
                    logout();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final int finalStatus = status;
            final JSONObject finalResponse = response;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(finalStatus, finalResponse));
            }
        });
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream stream = in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}
