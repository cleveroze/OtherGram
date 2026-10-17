package org.telegram.othergram.badges;

/**
 * A badge issued by OtherGram admins. Badges are visible only inside OtherGram and are
 * never presented as Telegram verification.
 */
public class OtherGramBadge {

    public static final String ICON_CHECK = "check";

    public final String id;
    public final String title;
    public final String description;
    public final int color;
    public final String icon;

    public OtherGramBadge(String id, String title, String description, int color, String icon) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.color = color;
        this.icon = icon != null ? icon : ICON_CHECK;
    }
}
