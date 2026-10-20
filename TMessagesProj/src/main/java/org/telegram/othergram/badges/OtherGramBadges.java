package org.telegram.othergram.badges;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Badge cache for all users OtherGram has seen. Names ask for badges while rendering;
 * unknown user ids are collected and handed to the registered {@link Fetcher} in batches,
 * which later calls {@link #put}. Without a fetcher (no backend) nothing is requested and
 * no badges are shown.
 */
public class OtherGramBadges {

    public interface Fetcher {
        /** Load badges for these users and report them with {@link #put}. Called on the UI thread. */
        void fetch(Set<Long> userIds);
    }

    private static final long BATCH_DELAY_MS = 300;

    private static final Map<Long, List<OtherGramBadge>> cache = new ConcurrentHashMap<>();
    private static final Set<Long> requested = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Long> pending = new HashSet<>();
    private static volatile Fetcher fetcher;

    static {
        OtherGramProfiles.init();
    }

    private static final Runnable flushPending = () -> {
        final Fetcher f = fetcher;
        if (f == null || pending.isEmpty()) {
            return;
        }
        final Set<Long> batch = new HashSet<>(pending);
        pending.clear();
        f.fetch(batch);
    };

    public static void setFetcher(Fetcher f) {
        fetcher = f;
    }

    /** Badges of the user, or an empty list when there are none or they are not loaded yet. */
    public static List<OtherGramBadge> get(long userId) {
        final List<OtherGramBadge> badges = cache.get(userId);
        if (badges == null) {
            request(userId);
            return Collections.emptyList();
        }
        return badges;
    }

    private static void request(long userId) {
        // Positive ids are users, negative ids are channels/groups (-chat.id)
        if (userId == 0 || fetcher == null || !requested.add(userId)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            pending.add(userId);
            AndroidUtilities.cancelRunOnUIThread(flushPending);
            AndroidUtilities.runOnUIThread(flushPending, BATCH_DELAY_MS);
        });
    }

    /** Stores fetched badges (an empty list means "no badges") and redraws names everywhere. */
    public static void put(Map<Long, List<OtherGramBadge>> badges) {
        for (Map.Entry<Long, List<OtherGramBadge>> e : badges.entrySet()) {
            cache.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        notifyNamesChanged();
    }

    /** Forgets cached badges so they are fetched again, e.g. after an admin changed them. */
    public static void invalidate(long userId) {
        cache.remove(userId);
        requested.remove(userId);
        notifyNamesChanged();
    }

    private static void notifyNamesChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.isValidAccount(a)) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                }
            }
        });
    }

    /**
     * Span with the user's badge pills, or null when the user has none.
     * @param compact one pill with a shortened caption, for tight places like the chat list
     */
    public static OtherGramBadgeSpan span(long userId, boolean compact) {
        final List<OtherGramBadge> badges = get(userId);
        return badges.isEmpty() ? null : new OtherGramBadgeSpan(badges, compact);
    }

    public static CharSequence append(CharSequence text, OtherGramBadgeSpan span) {
        if (span == null || text == null) {
            return text;
        }
        final SpannableStringBuilder sb = new SpannableStringBuilder(text);
        final int start = sb.length();
        sb.append(' ');
        sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** Name followed by the user's badges. */
    public static CharSequence appendBadges(CharSequence name, long userId) {
        return append(name, span(userId, false));
    }

    public static boolean hasBadges(long userId) {
        return !get(userId).isEmpty();
    }

    /** Bottom sheet explaining the user's badges; does nothing when there are none. */
    public static void showInfo(BaseFragment fragment, long userId) {
        final List<OtherGramBadge> badges = get(userId);
        final Context context = fragment != null ? fragment.getParentActivity() : null;
        if (badges.isEmpty() || context == null) {
            return;
        }
        final Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(12));

        final TextView header = new TextView(context);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        header.setText(getString(R.string.OG_BadgesTitle));
        layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        for (OtherGramBadge badge : badges) {
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            row.addView(new BadgeView(context, badge.color), LayoutHelper.createLinear(44, 44, 0, 0, 14, 0));

            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            final TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            title.setText(badge.label);
            texts.addView(title);
            final String details = badge.label.equals(badge.title) ? badge.description
                    : TextUtils.isEmpty(badge.description) ? badge.title : badge.title + " · " + badge.description;
            if (!TextUtils.isEmpty(details)) {
                final TextView description = new TextView(context);
                description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                description.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
                description.setText(details);
                texts.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            }
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            layout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6));
        }

        final TextView note = new TextView(context);
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        note.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
        note.setText(getString(R.string.OG_BadgeInfoNote));
        layout.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setCustomView(layout);
        fragment.showDialog(builder.create());
    }

    private static class BadgeView extends View {
        private final OtherGramBadgeDrawable drawable;

        BadgeView(Context context, int color) {
            super(context);
            drawable = new OtherGramBadgeDrawable(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        }
    }
}
