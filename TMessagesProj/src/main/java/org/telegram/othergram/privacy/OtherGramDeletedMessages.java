package org.telegram.othergram.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.othergram.OtherGramConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Keeps messages that others deleted (off by default). When enabled, remote delete updates are
 * not applied: the messages stay in the local cache and get a "deleted" mark next to the time.
 * Nothing leaves the device. Messages deleted from this device are removed as usual.
 *
 * Ids of private chats and groups are account-wide in Telegram, channel/supergroup ids are per
 * channel, so a key is (account, channel dialog id or 0, message id).
 */
public class OtherGramDeletedMessages {

    public static final String KEY_ENABLED = "keep_deleted_messages";

    private static final String PREFS = "othergram_deleted";
    private static final int MAX_STORED = 10000;

    private static final Object lock = new Object();
    private static final LinkedHashSet<String>[] marks = createMarks();

    @SuppressWarnings("unchecked")
    private static LinkedHashSet<String>[] createMarks() {
        return new LinkedHashSet[UserConfig.MAX_ACCOUNT_COUNT];
    }

    public static boolean isEnabled() {
        return OtherGramConfig.getBoolean(KEY_ENABLED, false);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static LinkedHashSet<String> load(int account) {
        LinkedHashSet<String> set = marks[account];
        if (set == null) {
            set = new LinkedHashSet<>();
            final String saved = prefs().getString("a" + account, "");
            if (!TextUtils.isEmpty(saved)) {
                for (String key : saved.split(",")) {
                    set.add(key);
                }
            }
            marks[account] = set;
        }
        return set;
    }

    /**
     * Called for remote delete updates. Returns true when the deletion must be skipped because the
     * feature is on; the ids are remembered to show the "deleted" mark.
     */
    public static boolean keep(int account, long channelDialogId, ArrayList<Integer> messageIds) {
        if (!isEnabled() || messageIds == null || messageIds.isEmpty()) {
            return false;
        }
        synchronized (lock) {
            final LinkedHashSet<String> set = load(account);
            for (Integer id : messageIds) {
                set.add(channelDialogId + ":" + id);
            }
            final Iterator<String> it = set.iterator();
            while (set.size() > MAX_STORED && it.hasNext()) {
                it.next();
                it.remove();
            }
            prefs().edit().putString("a" + account, TextUtils.join(",", set)).apply();
        }
        return true;
    }

    public static boolean isDeleted(int account, MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0) {
            return false;
        }
        final long channelId = message.messageOwner.peer_id != null ? message.messageOwner.peer_id.channel_id : 0;
        final String key = (channelId != 0 ? -channelId : 0) + ":" + message.getId();
        synchronized (lock) {
            return load(account).contains(key);
        }
    }

    public static void clearMarks() {
        synchronized (lock) {
            for (int a = 0; a < marks.length; a++) {
                marks[a] = null;
            }
            prefs().edit().clear().apply();
        }
    }
}
