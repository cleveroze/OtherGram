package org.telegram.othergram.ui;

import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.othergram.OtherGramConfig;

/**
 * Interface touches in the iOS 18 spirit: bouncy bottom tabs with a light haptic tick, the floating
 * "island" chat header and softer dialogs. Everything can be switched off in Appearance.
 */
public class OtherGramInterface {

    public static final String KEY_TAB_BOUNCE = "ui_tab_bounce";
    public static final String KEY_TAB_HAPTICS = "ui_tab_haptics";
    public static final String KEY_ISLAND_HEADER = "ui_island_header";

    /** Bottom tab icons jump with a spring when selected (on by default). */
    public static boolean tabBounce() {
        return OtherGramConfig.getBoolean(KEY_TAB_BOUNCE, true);
    }

    /** Light haptic tick when switching bottom tabs (on by default). */
    public static boolean tabHaptics() {
        return OtherGramConfig.getBoolean(KEY_TAB_HAPTICS, true);
    }

    /** Chat header drawn as floating round buttons and a name pill, like Telegram for iOS (off by default). */
    public static boolean islandHeader() {
        return OtherGramConfig.getBoolean(KEY_ISLAND_HEADER, false);
    }

    /**
     * Damped spring from 0 to 1: dips below 1 at the start, overshoots once and settles, like
     * an iOS tab icon. {@code t} is the fraction of the animation, 0..1.
     */
    public static float spring(float t) {
        return (float) (1 - Math.exp(-5.5 * t) * Math.cos(13 * t));
    }

    public static void tick(View view) {
        if (view != null && tabHaptics()) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {
            }
        }
    }
}
