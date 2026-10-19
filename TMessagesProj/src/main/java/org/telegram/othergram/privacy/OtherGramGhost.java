package org.telegram.othergram.privacy;

import org.telegram.othergram.OtherGramConfig;

/**
 * Ghost mode: each part can be switched on separately. Everything stays local: messages are still
 * marked read on this device, only the signals to Telegram's servers are not sent.
 */
public class OtherGramGhost {

    public static final String KEY_NO_READ = "ghost_no_read";
    public static final String KEY_NO_TYPING = "ghost_no_typing";
    public static final String KEY_NO_ONLINE = "ghost_no_online";
    public static final String KEY_NO_STORY_VIEWS = "ghost_no_story_views";

    /** Do not send read receipts (including "voice message listened"). */
    public static boolean hideRead() {
        return OtherGramConfig.getBoolean(KEY_NO_READ, false);
    }

    /** Do not send "typing…", "recording voice…" and similar statuses. */
    public static boolean hideTyping() {
        return OtherGramConfig.getBoolean(KEY_NO_TYPING, false);
    }

    /** Never report "online"; the account keeps showing the last-seen time. */
    public static boolean hideOnline() {
        return OtherGramConfig.getBoolean(KEY_NO_ONLINE, false);
    }

    /** Watch stories without the author seeing the view. */
    public static boolean hideStoryViews() {
        return OtherGramConfig.getBoolean(KEY_NO_STORY_VIEWS, false);
    }

    public static boolean isAnyEnabled() {
        return hideRead() || hideTyping() || hideOnline() || hideStoryViews();
    }
}
