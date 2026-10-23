package org.telegram.othergram;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Update check against the latest public GitHub release. Drafts are not public, so a version the
 * owner keeps for himself is never offered to anyone. Only the release tag is read; nothing is sent.
 */
public class OtherGramUpdates {

    private static final String LATEST_URL = "https://api.github.com/repos/cleveroze/OtherGram/releases/latest";
    private static final long CACHE_MS = 30 * 60 * 1000L;

    public interface Callback {
        /** {@code version} like "2.0.1" and the release page, or nulls when the check failed. */
        void onResult(String version, String url);
    }

    private static String cachedVersion;
    private static String cachedUrl;
    private static long cachedAt;

    public static void check(Callback callback) {
        if (cachedVersion != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            callback.onResult(cachedVersion, cachedUrl);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String version = null;
            String url = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                if (connection.getResponseCode() == 200) {
                    try (InputStream in = connection.getInputStream()) {
                        final JSONObject json = new JSONObject(new String(OtherGramBackup.readAll(in, 2_000_000), "UTF-8"));
                        version = json.optString("tag_name", "").replaceFirst("^v", "").replaceAll("-.*$", "");
                        url = json.optString("html_url", null);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final String v = version == null || version.isEmpty() ? null : version;
            final String u = url;
            AndroidUtilities.runOnUIThread(() -> {
                if (v != null) {
                    cachedVersion = v;
                    cachedUrl = u;
                    cachedAt = System.currentTimeMillis();
                }
                callback.onResult(v, u);
            });
        });
    }

    /** Whether {@code version} is newer than the installed OtherGram. */
    public static boolean isNewer(String version) {
        return compare(version, OtherGramConfig.VERSION) > 0;
    }

    static int compare(String a, String b) {
        final String[] pa = a.split("\\.");
        final String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            final int x = i < pa.length ? parse(pa[i]) : 0;
            final int y = i < pb.length ? parse(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
