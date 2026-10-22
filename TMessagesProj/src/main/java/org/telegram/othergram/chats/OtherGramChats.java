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

    public static final String KEY_SEND_ANIMATION_STYLE = "chats_send_animation_style";
    public static final int ANIM_TELEGRAM = 0;
    public static final int ANIM_SPRING = 1;
    public static final int ANIM_BOUNCE = 2;
    public static final int ANIM_JELLY = 3;
    public static final int ANIM_SLIDE = 4;
    public static final int ANIM_FADE = 5;
    public static final int[] ANIM_STYLES = {ANIM_TELEGRAM, ANIM_SPRING, ANIM_BOUNCE, ANIM_JELLY, ANIM_SLIDE, ANIM_FADE};

    /** Chosen send animation; 1.1.x stored only an on/off switch, which maps to the spring. */
    public static int sendAnimationStyle() {
        if (OtherGramConfig.contains(KEY_SEND_ANIMATION_STYLE)) {
            return OtherGramConfig.getInt(KEY_SEND_ANIMATION_STYLE, ANIM_TELEGRAM);
        }
        return OtherGramConfig.getBoolean(KEY_SEND_ANIMATION, false) ? ANIM_SPRING : ANIM_TELEGRAM;
    }

    public static boolean sendAnimation() {
        return sendAnimationStyle() != ANIM_TELEGRAM || org.telegram.othergram.theme.OtherGramMaterial.motion();
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
        if (org.telegram.othergram.theme.OtherGramMaterial.motion()) {
            playMaterialAnimation(view, out);
            return;
        }
        final int style = sendAnimationStyle();
        if (style == ANIM_TELEGRAM) {
            return;
        }
        final float density = view.getResources().getDisplayMetrics().density;
        view.setPivotX(out ? view.getWidth() : 0);
        view.setPivotY(view.getHeight());
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        switch (style) {
            case ANIM_BOUNCE:
                // Drops in from below and bounces a couple of times before settling
                animator.setDuration(620);
                animator.setInterpolator(new android.view.animation.BounceInterpolator());
                break;
            case ANIM_JELLY:
                animator.setDuration(560);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                break;
            case ANIM_SLIDE:
                animator.setDuration(380);
                animator.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
                break;
            case ANIM_FADE:
                animator.setDuration(320);
                animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
                break;
            default: // ANIM_SPRING
                animator.setDuration(360);
                animator.setInterpolator(new OvershootInterpolator(1.4f));
                break;
        }
        animator.addUpdateListener(a -> {
            final float t = (float) a.getAnimatedValue();
            final float fraction = a.getAnimatedFraction();
            switch (style) {
                case ANIM_BOUNCE: {
                    // Grows from the bottom corner and bounces in place (translationY is owned by the list)
                    final float scale = 0.7f + 0.3f * t;
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                    view.setAlpha(Math.min(1f, fraction * 3f));
                    break;
                }
                case ANIM_JELLY: {
                    // Squash and stretch that decays: wide-and-short, then tall-and-narrow, then rest
                    final double wave = Math.sin(fraction * Math.PI * 3) * (1f - fraction);
                    view.setScaleX((float) (1 + 0.12 * wave));
                    view.setScaleY((float) (1 - 0.12 * wave));
                    view.setAlpha(Math.min(1f, fraction * 4f));
                    break;
                }
                case ANIM_SLIDE:
                    view.setTranslationX((1f - t) * (out ? 1 : -1) * 48 * density);
                    view.setAlpha(t);
                    break;
                case ANIM_FADE:
                    view.setAlpha(t);
                    break;
                default: {
                    final float scale = 0.86f + 0.14f * t;
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                    view.setAlpha(Math.min(1f, 0.35f + 0.65f * t * 1.4f));
                    break;
                }
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setTranslationX(0f);
                view.setAlpha(1f);
                // translationY belongs to the list's own move animation, so it is left alone here
            }
        });
        animator.start();
    }

    /** Material "fade through": the message fades in while growing a little, with emphasized easing. */
    private static void playMaterialAnimation(View view, boolean out) {
        view.setPivotX(out ? view.getWidth() : 0);
        view.setPivotY(view.getHeight());
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f));
        animator.addUpdateListener(a -> {
            final float t = (float) a.getAnimatedValue();
            final float scale = 0.92f + 0.08f * t;
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setAlpha(Math.min(1f, t * 1.5f));
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
