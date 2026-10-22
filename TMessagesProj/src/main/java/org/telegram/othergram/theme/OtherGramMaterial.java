package org.telegram.othergram.theme;

import android.os.Build;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Material You mode: one seed color is turned into Material 3 tonal palettes and every interface
 * color of the app is taken from them (surfaces, text, icons, switches, buttons, menus, dialogs,
 * tabs). Chat bubbles, message text and the wallpaper are left alone, so the user's own bubble
 * customization keeps working. Applied in Theme.refreshThemeColors() after the user's color overrides.
 */
public class OtherGramMaterial {

    public static final String KEY_ENABLED = "material_enabled";
    public static final String KEY_PALETTE = "material_palette";
    public static final String KEY_MINIMAL = "material_minimal";
    public static final String KEY_MOTION = "material_motion";

    public static final String PALETTE_DYNAMIC = "dynamic";
    public static final String PALETTE_BLUE = "blue";
    public static final String PALETTE_PURPLE = "purple";
    public static final String PALETTE_VANILLA = "vanilla";
    public static final String PALETTE_GREEN = "green";

    public static final String[] PALETTES = {PALETTE_DYNAMIC, PALETTE_VANILLA, PALETTE_BLUE, PALETTE_PURPLE, PALETTE_GREEN};
    public static final int[] PALETTE_TITLES = {R.string.OG_MaterialDynamic, R.string.OG_MaterialVanilla,
            R.string.OG_MaterialBlue, R.string.OG_MaterialPurple, R.string.OG_MaterialGreen};

    private static final int SEED_VANILLA = 0xFFA68770;
    private static final int SEED_BLUE = 0xFF3F6FD1;
    private static final int SEED_PURPLE = 0xFF6A4BA6;
    private static final int SEED_GREEN = 0xFF4C7A55;

    public static boolean enabled() {
        return OtherGramConfig.getBoolean(KEY_ENABLED, false);
    }

    public static String palette() {
        return OtherGramConfig.getString(KEY_PALETTE, dynamicAvailable() ? PALETTE_DYNAMIC : PALETTE_VANILLA);
    }

    /** No dividers, no section shadows, flat surfaces (on by default). */
    public static boolean minimal() {
        return OtherGramConfig.getBoolean(KEY_MINIMAL, true);
    }

    /** Material motion: calm emphasized easing instead of springs and bounces (on by default). */
    public static boolean motion() {
        return enabled() && OtherGramConfig.getBoolean(KEY_MOTION, true);
    }

    public static boolean dynamicAvailable() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static int seed(String palette) {
        switch (palette) {
            case PALETTE_BLUE:
                return SEED_BLUE;
            case PALETTE_PURPLE:
                return SEED_PURPLE;
            case PALETTE_GREEN:
                return SEED_GREEN;
            case PALETTE_DYNAMIC:
                if (dynamicAvailable()) {
                    try {
                        return ApplicationLoader.applicationContext.getColor(android.R.color.system_accent1_500) | 0xFF000000;
                    } catch (Exception ignore) {
                    }
                }
                return SEED_VANILLA;
            default:
                return SEED_VANILLA;
        }
    }

    // ------------------------------------------------------------ tonal palettes

    /** A Material tonal palette approximated in HSL: same hue and colorfulness, lightness = tone. */
    private static final class Tones {
        final float hue;
        final float saturation;

        Tones(float hue, float saturation) {
            this.hue = hue;
            this.saturation = saturation;
        }

        int tone(int t) {
            // Very light and very dark tones get less saturated, as in Material's HCT palettes
            final float edge = Math.abs(t - 50) / 50f;
            final float s = saturation * (1f - 0.45f * edge * edge);
            return ColorUtils.HSLToColor(new float[]{hue, Math.max(0f, Math.min(1f, s)), t / 100f});
        }
    }

    /** Material 3 color roles for one theme. */
    public static final class Scheme {
        public int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public int secondaryContainer, onSecondaryContainer;
        public int surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
        public int onSurface, onSurfaceVariant, outline, outlineVariant;
    }

    public static Scheme scheme(int seed, boolean dark) {
        final float[] hsl = new float[3];
        ColorUtils.colorToHSL(seed, hsl);
        final float s = Math.max(0.28f, Math.min(0.85f, hsl[1]));
        final Tones primary = new Tones(hsl[0], s);
        final Tones secondary = new Tones(hsl[0], s * 0.38f);
        final Tones neutral = new Tones(hsl[0], Math.min(0.12f, s * 0.22f));
        final Tones neutralVariant = new Tones(hsl[0], Math.min(0.18f, s * 0.32f));

        final Scheme c = new Scheme();
        if (dark) {
            c.primary = primary.tone(80);
            c.onPrimary = primary.tone(20);
            c.primaryContainer = primary.tone(30);
            c.onPrimaryContainer = primary.tone(90);
            c.secondaryContainer = secondary.tone(30);
            c.onSecondaryContainer = secondary.tone(90);
            c.surface = neutral.tone(6);
            c.surfaceContainerLow = neutral.tone(10);
            c.surfaceContainer = neutral.tone(12);
            c.surfaceContainerHigh = neutral.tone(17);
            c.surfaceContainerHighest = neutral.tone(22);
            c.onSurface = neutral.tone(90);
            c.onSurfaceVariant = neutralVariant.tone(80);
            c.outline = neutralVariant.tone(60);
            c.outlineVariant = neutralVariant.tone(30);
        } else {
            c.primary = primary.tone(40);
            c.onPrimary = 0xFFFFFFFF;
            c.primaryContainer = primary.tone(90);
            c.onPrimaryContainer = primary.tone(10);
            c.secondaryContainer = secondary.tone(90);
            c.onSecondaryContainer = secondary.tone(10);
            c.surface = neutral.tone(98);
            c.surfaceContainerLow = neutral.tone(96);
            c.surfaceContainer = neutral.tone(94);
            c.surfaceContainerHigh = neutral.tone(92);
            c.surfaceContainerHighest = neutral.tone(90);
            c.onSurface = neutral.tone(10);
            c.onSurfaceVariant = neutralVariant.tone(30);
            c.outline = neutralVariant.tone(50);
            c.outlineVariant = neutralVariant.tone(80);
        }
        return c;
    }

    // ------------------------------------------------------------ applying

    /** Called from Theme.refreshThemeColors() after OtherGramColors.apply(). */
    public static void apply(SparseIntArray colors, boolean dark) {
        if (!enabled()) {
            return;
        }
        try {
            final Scheme c = scheme(seed(palette()), dark);
            final boolean minimal = minimal();

            // Surfaces: flat page, slightly raised cards and panels
            put(colors, c.surface, Theme.key_windowBackgroundGray, Theme.key_actionBarDefault, Theme.key_chats_menuBackground,
                    Theme.key_actionBarDefaultArchived, Theme.key_chats_menuTopBackground, Theme.key_chats_menuTopBackgroundCats,
                    Theme.key_avatar_backgroundActionBarBlue);
            put(colors, c.surfaceContainerLow, Theme.key_windowBackgroundWhite);
            put(colors, c.surfaceContainer, Theme.key_chat_messagePanelBackground, Theme.key_chat_emojiPanelBackground,
                    Theme.key_chat_topPanelBackground, Theme.key_glass_targetMainTabs, Theme.key_glass_targetMainTopPanel,
                    Theme.key_actionBarDefaultSubmenuBackground, Theme.key_chats_archiveBackground);
            put(colors, c.surfaceContainerHigh, Theme.key_dialogBackground, Theme.key_actionBarDefaultSearch,
                    Theme.key_chat_goDownButton, Theme.key_undo_background);
            put(colors, c.surfaceContainerHighest, Theme.key_dialogBackgroundGray, Theme.key_switchTrack,
                    Theme.key_chats_unreadCounterMuted, Theme.key_windowBackgroundWhiteInputField, Theme.key_dialogInputField);

            // Text
            put(colors, c.onSurface, Theme.key_windowBackgroundWhiteBlackText, Theme.key_chats_name, Theme.key_dialogTextBlack,
                    Theme.key_actionBarDefaultTitle, Theme.key_actionBarDefaultSubmenuItem, Theme.key_chats_menuItemText,
                    Theme.key_chats_menuName, Theme.key_chat_messagePanelText, Theme.key_chat_topPanelTitle, Theme.key_profile_title,
                    Theme.key_chats_nameMessage, Theme.key_glass_tabSelectedText);
            put(colors, c.onSurfaceVariant, Theme.key_windowBackgroundWhiteGrayText, Theme.key_windowBackgroundWhiteGrayText2,
                    Theme.key_windowBackgroundWhiteGrayText3, Theme.key_chats_message, Theme.key_chats_date, Theme.key_dialogTextGray3,
                    Theme.key_actionBarDefaultSubtitle, Theme.key_chats_menuPhone, Theme.key_windowBackgroundWhiteValueText,
                    Theme.key_chats_attachMessage, Theme.key_actionBarTabUnactiveText);
            put(colors, c.outline, Theme.key_windowBackgroundWhiteHintText, Theme.key_chat_messagePanelHint,
                    Theme.key_actionBarDefaultSearchPlaceholder, Theme.key_chats_muteIcon, Theme.key_chats_pinnedIcon,
                    Theme.key_checkboxSquareUnchecked, Theme.key_radioBackground);

            // Icons
            put(colors, c.onSurfaceVariant, Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_chats_menuItemIcon,
                    Theme.key_chat_messagePanelIcons, Theme.key_actionBarDefaultIcon, Theme.key_actionBarDefaultSubmenuItemIcon,
                    Theme.key_chat_emojiPanelIcon, Theme.key_glass_tabUnselected);

            // Primary accents
            put(colors, c.primary, Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueHeader,
                    Theme.key_windowBackgroundWhiteBlueButton, Theme.key_windowBackgroundWhiteBlueText, Theme.key_windowBackgroundWhiteBlueText2,
                    Theme.key_windowBackgroundWhiteLinkText, Theme.key_featuredStickers_addButton, Theme.key_switchTrackChecked,
                    Theme.key_switchTrackBlueChecked, Theme.key_radioBackgroundChecked, Theme.key_checkboxSquareBackground,
                    Theme.key_checkbox, Theme.key_dialogRadioBackgroundChecked, Theme.key_dialogCheckboxSquareBackground,
                    Theme.key_chat_messagePanelSend, Theme.key_chats_unreadCounter, Theme.key_dialogButton, Theme.key_dialogTextBlue,
                    Theme.key_dialogTextBlue2, Theme.key_dialogLineProgress, Theme.key_chats_onlineCircle, Theme.key_chat_messageLinkIn,
                    Theme.key_chats_sentCheck, Theme.key_chats_sentReadCheck, Theme.key_chats_secretName, Theme.key_chats_draft,
                    Theme.key_chats_actionMessage, Theme.key_chats_verifiedBackground, Theme.key_windowBackgroundWhiteInputFieldActivated,
                    Theme.key_dialogInputFieldActivated, Theme.key_progressCircle, Theme.key_fastScrollActive, Theme.key_undo_cancelColor,
                    Theme.key_chat_emojiPanelIconSelected, Theme.key_actionBarTabActiveText, Theme.key_actionBarTabLine,
                    Theme.key_glass_tabSelected, Theme.key_windowBackgroundChecked, Theme.key_contextProgressInner1);
            put(colors, c.onPrimary, Theme.key_chats_unreadCounterText, Theme.key_featuredStickers_buttonText,
                    Theme.key_checkboxSquareCheck, Theme.key_checkboxCheck, Theme.key_windowBackgroundCheckText);
            put(colors, c.secondaryContainer, Theme.key_switchTrackBlue, Theme.key_chat_messagePanelVoiceBackground);

            // Floating button and profile actions in tonal containers, like Material 3 FABs
            put(colors, c.primaryContainer, Theme.key_chats_actionBackground, Theme.key_profile_actionBackground);
            put(colors, ColorUtils.blendARGB(c.primaryContainer, c.onPrimaryContainer, 0.12f),
                    Theme.key_chats_actionPressedBackground, Theme.key_featuredStickers_addButtonPressed);
            put(colors, c.onPrimaryContainer, Theme.key_chats_actionIcon, Theme.key_profile_actionIcon);
            put(colors, c.onSurface & 0x00FFFFFF | 0x14000000, Theme.key_listSelector, Theme.key_actionBarDefaultSelector,
                    Theme.key_dialogButtonSelector, Theme.key_actionBarTabSelector, Theme.key_chats_pinnedOverlay,
                    Theme.key_chats_tabletSelectedOverlay);
            put(colors, c.onSurface, Theme.key_undo_infoColor);

            // Minimalism: no lines between rows, no shadows between sections
            if (minimal) {
                colors.put(Theme.key_divider, 0x00000000);
                colors.put(Theme.key_windowBackgroundGrayShadow, c.surface);
            } else {
                colors.put(Theme.key_divider, c.outlineVariant);
            }
        } catch (Throwable ignore) {
            // A broken palette must never break the theme
        }
    }

    private static void put(SparseIntArray colors, int color, int... keys) {
        for (int key : keys) {
            colors.put(key, color);
        }
    }
}
