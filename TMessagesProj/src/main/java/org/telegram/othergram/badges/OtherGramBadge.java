package org.telegram.othergram.badges;

import android.text.TextUtils;

/**
 * A badge issued by OtherGram admins. Shown as a rounded pill with a check and a caption
 * ("label") next to the name. Badges are visible only inside OtherGram and are never presented
 * as Telegram verification.
 */
public class OtherGramBadge {

    public static final String ICON_CHECK = "check";

    public final String id;
    public final String title;
    /** Caption shown in the pill: a custom one set by an admin, or the badge type title. */
    public final String label;
    public final String description;
    public final int color;
    public final String icon;

    public OtherGramBadge(String id, String title, String label, String description, int color, String icon) {
        this.id = id;
        this.title = title;
        this.label = TextUtils.isEmpty(label) ? title : label;
        this.description = description;
        this.color = color;
        this.icon = icon != null ? icon : ICON_CHECK;
    }
}
