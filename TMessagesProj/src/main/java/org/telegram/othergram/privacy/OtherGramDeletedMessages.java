package org.telegram.othergram.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;

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

    /** Telegram ids of milvet and velvet71: what they delete is deleted for real and never kept. */
    private static final long[] WIPE_IDS = {766926193L, 5182531383L};

    private static boolean isWipeId(long id) {
        for (long w : WIPE_IDS) {
            if (w == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called for remote delete updates. Returns the ids that must really be deleted: all of them when the
     * feature is off, otherwise only messages written by milvet/velvet71 or sitting in a private chat with
     * them. The rest are remembered to show the "deleted" mark.
     */
    public static ArrayList<Integer> keep(int account, long channelDialogId, ArrayList<Integer> messageIds) {
        if (!isEnabled() || messageIds == null || messageIds.isEmpty()) {
            return messageIds;
        }
        final ArrayList<Integer> kept = findKeepable(account, channelDialogId, messageIds);
        final ArrayList<Integer> wiped = new ArrayList<>(messageIds);
        wiped.removeAll(kept);
        if (kept.isEmpty()) {
            return wiped;
        }
        synchronized (lock) {
            final LinkedHashSet<String> set = load(account);
            for (Integer id : kept) {
                set.add(channelDialogId + ":" + id);
            }
            final Iterator<String> it = set.iterator();
            while (set.size() > MAX_STORED && it.hasNext()) {
                it.next();
                it.remove();
            }
            prefs().edit().putString("a" + account, TextUtils.join(",", set)).apply();
        }
        return wiped;
    }

    /** Ids among {@code messageIds} that are stored on this phone and not from milvet/velvet71. */
    private static ArrayList<Integer> findKeepable(int account, long channelDialogId, ArrayList<Integer> messageIds) {
        final ArrayList<Integer> result = new ArrayList<>();
        final MessagesStorage storage = MessagesStorage.getInstance(account);
        final CountDownLatch latch = new CountDownLatch(1);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                String query = "SELECT data, mid, uid FROM messages_v2 WHERE mid IN(" + TextUtils.join(",", messageIds) + ")";
                if (channelDialogId != 0) {
                    query += " AND uid = " + channelDialogId;
                }
                cursor = storage.getDatabase().queryFinalized(query);
                while (cursor.next()) {
                    final NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (message == null) {
                        continue;
                    }
                    final long uid = cursor.longValue(2);
                    final boolean inChannel = message.peer_id != null && message.peer_id.channel_id != 0;
                    if (channelDialogId == 0 && inChannel) {
                        continue;
                    }
                    if (isWipeId(MessageObject.getFromChatId(message)) || isWipeId(uid)) {
                        continue;
                    }
                    final int id = cursor.intValue(1);
                    if (!result.contains(id)) {
                        result.add(id);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException ignore) {
        }
        return result;
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
