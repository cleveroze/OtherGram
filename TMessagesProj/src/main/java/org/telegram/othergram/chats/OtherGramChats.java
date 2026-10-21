package org.telegram.othergram.chats;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.LocaleController;
import org.telegram.othergram.OtherGramConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local chat settings from the "Chats" section. Each one is off by default and only changes how this
 * device shows chats.
 */
public class OtherGramChats {

    public static final String KEY_SECONDS = "chats_time_seconds";
    public static final String KEY_NO_ROUNDING = "chats_no_number_rounding";
    public static final String KEY_SEND_ANIMATION = "chats_send_animation";
    public static final String KEY_HIDE_EDITED = "chats_hide_edited";
    public static final String KEY_HIDE_SHARE = "chats_hide_share_button";

    private static SimpleDateFormat seconds24;
    private static SimpleDateFormat seconds12;

    /** Show message times with seconds. */
    public static boolean showSeconds() {
        return OtherGramConfig.getBoolean(KEY_SECONDS, false);
    }

    /** Show exact counters (1234) instead of rounded ones (1.2K). */
    public static boolean noNumberRounding() {
        return OtherGramConfig.getBoolean(KEY_NO_ROUNDING, false);
    }

    public static boolean sendAnimation() {
        return OtherGramConfig.getBoolean(KEY_SEND_ANIMATION, false);
    }

    public static boolean hideEdited() {
        return OtherGramConfig.getBoolean(KEY_HIDE_EDITED, false);
    }

    public static boolean hideShareButton() {
        return OtherGramConfig.getBoolean(KEY_HIDE_SHARE, false);
    }

    /** Message time as Telegram shows it, plus seconds when enabled. */
    public static String formatMessageTime(long millis) {
        if (!showSeconds()) {
            return LocaleController.getInstance().getFormatterDay().format(millis);
        }
        if (LocaleController.is24HourFormat) {
            if (seconds24 == null) {
                seconds24 = new SimpleDateFormat("HH:mm:ss", Locale.US);
            }
            return seconds24.format(new Date(millis));
        }
        if (seconds12 == null) {
            seconds12 = new SimpleDateFormat("h:mm:ss a", Locale.US);
        }
        return seconds12.format(new Date(millis));
    }

    /**
     * Soft "pop" for a message that was just sent: it grows from the bottom corner with a light
     * overshoot. Runs on its own animator so it never interferes with the list's move animation.
     */
    public static void playSendAnimation(View view, boolean out) {
        view.setPivotX(out ? view.getWidth() : 0);
        view.setPivotY(view.getHeight());
        view.setScaleX(0.86f);
        view.setScaleY(0.86f);
        view.setAlpha(0.35f);
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(360);
        animator.setInterpolator(new OvershootInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            final float t = (float) a.getAnimatedValue();
            final float scale = 0.86f + 0.14f * t;
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setAlpha(Math.min(1f, 0.35f + 0.65f * t * 1.4f));
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
            }
        });
        animator.start();
    }
}
