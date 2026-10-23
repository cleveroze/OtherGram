package org.telegram.othergram.badges;

import android.text.TextUtils;

/**
 * A badge issued by OtherGram admins. Shown as a rounded pill with a check (or a cross) and a
 * caption ("label") next to the name. Badges are visible only inside OtherGram and are never
 * presented as Telegram verification.
 */
public class OtherGramBadge {

    public static final String ICON_CHECK = "check";
    public static final String ICON_CROSS = "cross";
    public static final String STYLE_NORMAL = "normal";
    /** Animated shimmering rainbow fill; only admins create badge types, so only admins can give it. */
    public static final String STYLE_RAINBOW = "rainbow";

    public final String id;
    public final String title;
    /** Caption shown in the pill: a custom one set by an admin, or the badge type title. */
    public final String label;
    public final String description;
    public final int color;
    public final String icon;
    public final String style;

    public OtherGramBadge(String id, String title, String label, String description, int color, String icon) {
        this(id, title, label, description, color, icon, STYLE_NORMAL);
    }

    public OtherGramBadge(String id, String title, String label, String description, int color, String icon, String style) {
        this.id = id;
        this.title = title;
        this.label = TextUtils.isEmpty(label) ? title : label;
        this.description = description;
        this.color = color;
        this.icon = ICON_CROSS.equals(icon) ? ICON_CROSS : ICON_CHECK;
        this.style = STYLE_RAINBOW.equals(style) ? STYLE_RAINBOW : STYLE_NORMAL;
    }

    public boolean isRainbow() {
        return STYLE_RAINBOW.equals(style);
    }

    public boolean isCross() {
        return ICON_CROSS.equals(icon);
    }
}
