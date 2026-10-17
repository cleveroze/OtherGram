package org.telegram.othergram.network;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Proves to the backend that this app controls the current Telegram account:
 * the app sends /start &lt;nonce&gt; to the auth bot from that account (Telegram guarantees
 * the sender), then exchanges nonce + secret for a session token.
 */
public class OtherGramAuth {

    public interface Callback {
        void onDone(boolean success, String error);
    }

    private static final long POLL_INTERVAL_MS = 1500;
    private static final int MAX_POLLS = 40;

    private static boolean inProgress;

    public static void login(int account, Callback callback) {
        if (inProgress) {
            return;
        }
        if (!OtherGramApi.isConfigured()) {
            callback.onDone(false, "server is not configured");
            return;
        }
        inProgress = true;
        final String secret = randomHex(32);
        final JSONObject start = new JSONObject();
        try {
            start.put("secret_hash", sha256Hex(secret));
        } catch (Exception e) {
            finish(callback, false, e.getMessage());
            return;
        }
        OtherGramApi.sendJson("POST", "/v1/auth/start", start, (status, response) -> {
            if (status != 200 || response == null) {
                finish(callback, false, "auth start failed: " + status);
                return;
            }
            final String nonce = response.optString("nonce");
            final String botUsername = response.optString("bot_username");
            final MessagesController controller = MessagesController.getInstance(account);
            controller.getUserNameResolver().resolve(botUsername, peerId -> {
                final TLRPC.User bot = peerId != null ? controller.getUser(peerId) : null;
                if (bot == null || !bot.bot) {
                    finish(callback, false, "auth bot not found");
                    return;
                }
                controller.sendBotStart(bot, nonce);
                poll(nonce, secret, 0, callback);
            });
        });
    }

    private static void poll(String nonce, String secret, int attempt, Callback callback) {
        if (attempt >= MAX_POLLS) {
            finish(callback, false, "confirmation timed out");
            return;
        }
        final JSONObject body = new JSONObject();
        try {
            body.put("nonce", nonce);
            body.put("secret", secret);
        } catch (Exception e) {
            finish(callback, false, e.getMessage());
            return;
        }
        OtherGramApi.sendJson("POST", "/v1/auth/finish", body, (status, response) -> {
            if (status == 202) {
                AndroidUtilities.runOnUIThread(() -> poll(nonce, secret, attempt + 1, callback), POLL_INTERVAL_MS);
            } else if (status == 200 && response != null) {
                OtherGramApi.saveSession(response.optString("token"), response.optLong("user_id"), response.optBoolean("is_admin"));
                finish(callback, true, null);
            } else {
                finish(callback, false, "auth finish failed: " + status);
            }
        });
    }

    private static void finish(Callback callback, boolean success, String error) {
        inProgress = false;
        callback.onDone(success, error);
    }

    private static String randomHex(int bytes) {
        final byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return Utilities.bytesToHex(buf).toLowerCase();
    }

    static String sha256Hex(String text) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        return Utilities.bytesToHex(digest).toLowerCase();
    }
}
