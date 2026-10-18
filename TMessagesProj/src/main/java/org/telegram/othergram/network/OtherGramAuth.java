package org.telegram.othergram.network;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CODE_PATTERN = Pattern.compile("OtherGram code: (\\d{8})");
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
                final int startedAt = ConnectionsManager.getInstance(account).getCurrentTime() - 5;
                controller.sendBotStart(bot, nonce);
                poll(account, bot, startedAt, nonce, secret, 0, callback);
            });
        });
    }

    /**
     * Waits for the bot to answer in this account's own chat with "OtherGram code: NNNNNNNN" and
     * sends that code with the request. Someone who started a request elsewhere and lured this
     * user into /start cannot see that chat, so they never get the code.
     */
    private static void poll(int account, TLRPC.User bot, int startedAt, String nonce, String secret, int attempt, Callback callback) {
        if (attempt >= MAX_POLLS) {
            finish(callback, false, "confirmation timed out");
            return;
        }
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(account).getInputPeer(bot.id);
        req.limit = 5;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, error) -> AndroidUtilities.runOnUIThread(() -> {
            final String code = res instanceof TLRPC.messages_Messages ? findCode((TLRPC.messages_Messages) res, bot.id, startedAt) : null;
            if (code == null) {
                AndroidUtilities.runOnUIThread(() -> poll(account, bot, startedAt, nonce, secret, attempt + 1, callback), POLL_INTERVAL_MS);
            } else {
                submit(nonce, secret, code, callback);
            }
        }));
    }

    private static String findCode(TLRPC.messages_Messages history, long botId, int startedAt) {
        for (TLRPC.Message message : history.messages) {
            if (message == null || message.out || message.date < startedAt || message.message == null
                    || MessageObject.getFromChatId(message) != botId) {
                continue;
            }
            final Matcher m = CODE_PATTERN.matcher(message.message);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static void submit(String nonce, String secret, String code, Callback callback) {
        final JSONObject body = new JSONObject();
        try {
            body.put("nonce", nonce);
            body.put("secret", secret);
            body.put("code", code);
        } catch (Exception e) {
            finish(callback, false, e.getMessage());
            return;
        }
        OtherGramApi.sendJson("POST", "/v1/auth/finish", body, (status, response) -> {
            if (status == 200 && response != null) {
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
