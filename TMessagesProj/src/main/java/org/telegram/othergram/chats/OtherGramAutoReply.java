package org.telegram.othergram.chats;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Auto-reply in private chats: when someone writes to you, OtherGram answers once with your text,
 * then stays quiet with that person for the chosen period. Works on this phone only, so the phone
 * has to be on and online (there is no server copy of your account).
 */
public class OtherGramAutoReply {

    public static final String KEY_ENABLED = "autoreply_enabled";
    public static final String KEY_TEXT = "autoreply_text";
    public static final String KEY_ONLY_STRANGERS = "autoreply_only_strangers";
    public static final String KEY_SCHEDULE = "autoreply_schedule";
    public static final String KEY_COOLDOWN_HOURS = "autoreply_cooldown_hours";

    /** Schedules: always, or a nightly window "from-to" in hours. */
    public static final int[][] SCHEDULES = {{-1, -1}, {23, 8}, {22, 9}, {0, 10}, {9, 18}};
    public static final int[] COOLDOWNS = {1, 6, 12, 24};

    private static final long MAX_MESSAGE_AGE_SECONDS = 10 * 60;
    private static final long SERVICE_USER_ID = 777000;

    public static boolean enabled() {
        return OtherGramConfig.getBoolean(KEY_ENABLED, false) && !TextUtils.isEmpty(text());
    }

    public static String text() {
        return OtherGramConfig.getString(KEY_TEXT, "");
    }

    public static boolean onlyStrangers() {
        return OtherGramConfig.getBoolean(KEY_ONLY_STRANGERS, false);
    }

    public static int schedule() {
        final int s = OtherGramConfig.getInt(KEY_SCHEDULE, 0);
        return s >= 0 && s < SCHEDULES.length ? s : 0;
    }

    public static int cooldownHours() {
        return OtherGramConfig.getInt(KEY_COOLDOWN_HOURS, 12);
    }

    private static SharedPreferences sent() {
        return ApplicationLoader.applicationContext.getSharedPreferences("othergram_autoreply", Context.MODE_PRIVATE);
    }

    static boolean inSchedule() {
        final int[] window = SCHEDULES[schedule()];
        if (window[0] < 0) {
            return true;
        }
        final int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return window[0] <= window[1] ? hour >= window[0] && hour < window[1] : hour >= window[0] || hour < window[1];
    }

    /** Called by Telegram's notification code for every batch of new messages (also when woken by a push). */
    public static void onNewMessages(int account, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty() || !enabled() || !inSchedule()) {
            return;
        }
        try {
            final long selfId = UserConfig.getInstance(account).getClientUserId();
            final int now = ConnectionsManager.getInstance(account).getCurrentTime();
            for (MessageObject message : messages) {
                if (message == null || message.messageOwner == null || message.isOutOwner() || message.messageOwner.action != null) {
                    continue;
                }
                final long dialogId = message.getDialogId();
                if (dialogId <= 0 || dialogId == selfId || dialogId == SERVICE_USER_ID || message.scheduled
                        || now - message.messageOwner.date > MAX_MESSAGE_AGE_SECONDS) {
                    continue;
                }
                maybeReply(account, dialogId, now);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void maybeReply(int account, long dialogId, int now) {
        final String key = account + "_" + dialogId;
        final long last = sent().getLong(key, 0);
        if (now - last < cooldownHours() * 3600L) {
            return;
        }
        // Mark first, so several messages arriving together get a single answer
        sent().edit().putLong(key, now).apply();
        AndroidUtilities.runOnUIThread(() -> {
            final TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user == null || user.bot || user.deleted || UserObject.isReplyUser(user) || user.support) {
                return;
            }
            if (onlyStrangers() && user.contact) {
                return;
            }
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text(), dialogId));
        });
    }
}
