package org.telegram.othergram.theme;

import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * User color overrides on top of any theme. Each group maps one chosen color onto every theme key
 * that shows that kind of element, separately for light and dark themes. Applied inside
 * Theme.refreshThemeColors(), so the whole app repaints at once.
 */
public class OtherGramColors {

    public static class Group {
        public final String id;
        public final int titleRes;
        final int[] keys;
        /** Keys painted with a slightly shifted shade of the chosen color (pressed/selected states). */
        final int[] shadedKeys;

        Group(String id, int titleRes, int[] keys, int[] shadedKeys) {
            this.id = id;
            this.titleRes = titleRes;
            this.keys = keys;
            this.shadedKeys = shadedKeys;
        }

        String configKey() {
            return "color_" + id;
        }
    }

    public static final Group ACCENT = new Group("accent", R.string.OG_ColorAccent, new int[]{
            Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueHeader, Theme.key_windowBackgroundWhiteBlueButton,
            Theme.key_featuredStickers_addButton, Theme.key_switchTrackChecked, Theme.key_radioBackgroundChecked,
            Theme.key_checkboxSquareBackground, Theme.key_chat_messagePanelSend, Theme.key_chats_actionBackground,
            Theme.key_chats_unreadCounter, Theme.key_dialogButton, Theme.key_chats_onlineCircle,
            Theme.key_chat_messageLinkIn, Theme.key_chats_sentCheck, Theme.key_chats_sentReadCheck}, new int[0]);

    public static final Group BACKGROUND = new Group("background", R.string.OG_ColorBackground, new int[]{
            Theme.key_windowBackgroundWhite, Theme.key_dialogBackground, Theme.key_chats_menuBackground,
            Theme.key_chat_messagePanelBackground}, new int[0]);

    public static final Group SECONDARY_BACKGROUND = new Group("background2", R.string.OG_ColorSecondaryBackground, new int[]{
            Theme.key_windowBackgroundGray, Theme.key_dialogBackgroundGray}, new int[0]);

    public static final Group HEADER = new Group("header", R.string.OG_ColorHeader, new int[]{
            Theme.key_actionBarDefault}, new int[0]);

    public static final Group HEADER_TEXT = new Group("header_text", R.string.OG_ColorHeaderText, new int[]{
            Theme.key_actionBarDefaultTitle, Theme.key_actionBarDefaultIcon, Theme.key_profile_title}, new int[0]);

    public static final Group TEXT = new Group("text", R.string.OG_ColorText, new int[]{
            Theme.key_windowBackgroundWhiteBlackText, Theme.key_dialogTextBlack, Theme.key_chats_name}, new int[0]);

    public static final Group SECONDARY_TEXT = new Group("text2", R.string.OG_ColorSecondaryText, new int[]{
            Theme.key_windowBackgroundWhiteGrayText, Theme.key_windowBackgroundWhiteGrayText2, Theme.key_windowBackgroundWhiteGrayText3,
            Theme.key_chats_message, Theme.key_chats_date, Theme.key_dialogTextGray3, Theme.key_actionBarDefaultSubtitle}, new int[0]);

    public static final Group ICONS = new Group("icons", R.string.OG_ColorIcons, new int[]{
            Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_chats_menuItemIcon, Theme.key_chat_messagePanelIcons}, new int[0]);

    public static final Group IN_BUBBLE = new Group("in_bubble", R.string.OG_ColorInBubble, new int[]{
            Theme.key_chat_inBubble}, new int[]{Theme.key_chat_inBubbleSelected});

    public static final Group OUT_BUBBLE = new Group("out_bubble", R.string.OG_ColorOutBubble, new int[]{
            Theme.key_chat_outBubble}, new int[]{Theme.key_chat_outBubbleSelected});

    public static final Group MESSAGE_TEXT = new Group("message_text", R.string.OG_ColorMessageText, new int[]{
            Theme.key_chat_messageTextIn, Theme.key_chat_messageTextOut}, new int[0]);

    public static final Group TAB_BAR = new Group("tab_bar", R.string.OG_ColorTabBar, new int[]{
            Theme.key_glass_targetMainTabs, Theme.key_glass_targetMainTopPanel}, new int[0]);

    public static final Group TAB_ICONS = new Group("tab_icons", R.string.OG_ColorTabIcons, new int[]{
            Theme.key_glass_tabUnselected}, new int[0]);

    public static final Group TAB_SELECTED = new Group("tab_selected", R.string.OG_ColorTabSelected, new int[]{
            Theme.key_glass_tabSelected, Theme.key_glass_tabSelectedText}, new int[0]);

    public static final Group[] GROUPS = {
            ACCENT, BACKGROUND, SECONDARY_BACKGROUND, HEADER, HEADER_TEXT, TEXT, SECONDARY_TEXT, ICONS,
            IN_BUBBLE, OUT_BUBBLE, MESSAGE_TEXT, TAB_BAR, TAB_ICONS, TAB_SELECTED
    };

    /** Called from Theme.refreshThemeColors() after the theme and its accent are applied. */
    public static void apply(SparseIntArray colors, boolean dark) {
        for (Group group : GROUPS) {
            final String key = OtherGramConfig.themedKey(group.configKey(), dark);
            if (!OtherGramConfig.contains(key)) {
                continue;
            }
            final int color = OtherGramConfig.getInt(key, 0) | 0xFF000000;
            for (int k : group.keys) {
                colors.put(k, keepAlpha(colors.get(k, 0xFF000000), color));
            }
            final int shaded = ColorUtils.blendARGB(color, dark ? 0xFFFFFFFF : 0xFF000000, 0.08f);
            for (int k : group.shadedKeys) {
                colors.put(k, keepAlpha(colors.get(k, 0xFF000000), shaded));
            }
        }
    }

    /** Some theme keys are translucent on purpose (e.g. secondary text in the dark theme); keep their alpha. */
    private static int keepAlpha(int original, int color) {
        final int alpha = original >>> 24;
        return (alpha == 0 ? 0xFF000000 : alpha << 24) | (color & 0x00FFFFFF);
    }

    public static boolean hasOverride(Group group, boolean dark) {
        return OtherGramConfig.contains(OtherGramConfig.themedKey(group.configKey(), dark));
    }

    /** Color currently shown for the group in the active theme. */
    public static int getColor(Group group) {
        return Theme.getColor(group.keys[0]) | 0xFF000000;
    }

    public static void setColor(Group group, boolean dark, int color) {
        OtherGramConfig.putThemedInt(group.configKey(), dark, color | 0xFF000000);
        Theme.refreshThemeColors();
    }

    public static void reset(Group group, boolean dark) {
        OtherGramConfig.removeThemed(group.configKey(), dark);
        Theme.refreshThemeColors();
    }

    public static void resetAll(boolean dark) {
        for (Group group : GROUPS) {
            OtherGramConfig.removeThemed(group.configKey(), dark);
        }
        Theme.refreshThemeColors();
    }

    // ------------------------------------------------------------ presets

    public static class Preset {
        public final int titleRes;
        final Group[] groups;
        final int[] light;
        final int[] dark;

        Preset(int titleRes, Group[] groups, int[] light, int[] dark) {
            this.titleRes = titleRes;
            this.groups = groups;
            this.light = light;
            this.dark = dark;
        }

        public int previewColor(boolean isDark) {
            return (isDark ? dark : light)[0];
        }
    }

    private static final Group[] PRESET_GROUPS = {ACCENT, BACKGROUND, SECONDARY_BACKGROUND, HEADER, OUT_BUBBLE, TAB_SELECTED};

    public static final Preset[] PRESETS = {
            new Preset(R.string.OG_PresetMocha, PRESET_GROUPS,
                    new int[]{0xFF7E5F49, 0xFFEFE3D6, 0xFFE2D2C1, 0xFFEFE3D6, 0xFFD9C5B1, 0xFF7E5F49},
                    new int[]{0xFFC4A58C, 0xFF241C17, 0xFF19130F, 0xFF241C17, 0xFF5E4838, 0xFFC4A58C}),
            new Preset(R.string.OG_PresetSage, PRESET_GROUPS,
                    new int[]{0xFF6F7F5E, 0xFFEEF0E6, 0xFFDFE3D3, 0xFFEEF0E6, 0xFFD4DCC5, 0xFF6F7F5E},
                    new int[]{0xFFA9B897, 0xFF1D211A, 0xFF151813, 0xFF1D211A, 0xFF46523B, 0xFFA9B897}),
            new Preset(R.string.OG_PresetRose, PRESET_GROUPS,
                    new int[]{0xFFA86F78, 0xFFF6E9E7, 0xFFEBD8D5, 0xFFF6E9E7, 0xFFE8CFCB, 0xFFA86F78},
                    new int[]{0xFFD6A1A8, 0xFF241A1B, 0xFF1A1314, 0xFF241A1B, 0xFF5C3F43, 0xFFD6A1A8}),
            new Preset(R.string.OG_PresetSlate, PRESET_GROUPS,
                    new int[]{0xFF5E6E80, 0xFFECEFF2, 0xFFDCE1E7, 0xFFECEFF2, 0xFFCFD8E2, 0xFF5E6E80},
                    new int[]{0xFF9FB1C4, 0xFF1A1E23, 0xFF13161A, 0xFF1A1E23, 0xFF3C4A5A, 0xFF9FB1C4}),
    };

    public static void applyPreset(Preset preset, boolean dark) {
        final int[] colors = dark ? preset.dark : preset.light;
        for (int i = 0; i < preset.groups.length; i++) {
            OtherGramConfig.putThemedInt(preset.groups[i].configKey(), dark, colors[i]);
        }
        Theme.refreshThemeColors();
    }
}
