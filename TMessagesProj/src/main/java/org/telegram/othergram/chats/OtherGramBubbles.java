package org.telegram.othergram.chats;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.othergram.badges.OtherGramBadges;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.ui.ActionBar.MessageDrawable;

/**
 * Bubble style a user publishes: a solid color or "liquid glass", with a transparency level. Own
 * outgoing bubbles use the local copy of these settings; other people's bubbles use what they
 * published, so OtherGram users see each other's bubbles the way their owners styled them.
 *
 * The color is adjusted automatically when the viewer's text color would be hard to read on it.
 */
public class OtherGramBubbles {

    public static final String STYLE_SOLID = "solid";
    public static final String STYLE_GLASS = "glass";

    public static final String KEY_STYLE = "bubble_style";
    public static final String KEY_COLOR = "bubble_color";
    public static final String KEY_ALPHA = "bubble_alpha";

    private static final double MIN_TEXT_CONTRAST = 3.5;

    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        edgePaint.setStyle(Paint.Style.STROKE);
    }

    /** Own bubble style (published to the server from Profile customization), or null. */
    public static String myStyle() {
        return OtherGramConfig.getString(KEY_STYLE, null);
    }

    public static int myColor() {
        return OtherGramConfig.getInt(KEY_COLOR, 0xFFA68770);
    }

    public static int myAlpha() {
        return OtherGramConfig.getInt(KEY_ALPHA, 100);
    }

    /**
     * Draws the bubble in the sender's style. Returns false when the message has no custom style and
     * Telegram should draw its own bubble.
     */
    public static boolean draw(Canvas canvas, MessageDrawable drawable, MessageObject message, int alpha, int textColor) {
        if (drawable == null || message == null) {
            return false;
        }
        final String style;
        final int color;
        final int opacity;
        if (message.isOutOwner()) {
            style = myStyle();
            color = myColor();
            opacity = myAlpha();
        } else {
            final long fromId = message.getFromChatId();
            if (fromId <= 0) {
                return false;
            }
            // Asks for the sender's published data if it is not cached yet (e.g. group members whose
            // names Telegram draws itself); the bubble gets the style on the next redraw
            OtherGramBadges.get(fromId);
            final OtherGramProfiles.Profile profile = OtherGramProfiles.getProfile(fromId);
            if (profile == null) {
                return false;
            }
            style = profile.bubbleStyle;
            color = profile.bubbleColor;
            opacity = profile.bubbleAlpha;
        }
        if (style == null) {
            return false;
        }
        final Rect b = drawable.getBounds();
        if (b.isEmpty()) {
            return false;
        }
        final int readable = readable(color | 0xFF000000, textColor);
        final float opacityK = Math.max(0.2f, Math.min(1f, opacity / 100f)) * (alpha / 255f);

        if (STYLE_GLASS.equals(style)) {
            // Liquid glass: a translucent tint, a soft highlight on the upper part and a thin light edge
            fillPaint.setShader(null);
            fillPaint.setColor(readable);
            fillPaint.setAlpha((int) (255 * 0.62f * opacityK));
            drawable.draw(canvas, fillPaint);

            sheenPaint.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.6f,
                    0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            sheenPaint.setAlpha((int) (255 * opacityK));
            drawable.draw(canvas, sheenPaint);

            edgePaint.setStrokeWidth(Math.max(1f, AndroidUtilities.dp(0.8f)));
            edgePaint.setColor(0x66FFFFFF);
            edgePaint.setAlpha((int) (0x66 * opacityK));
            drawable.draw(canvas, edgePaint);
        } else {
            fillPaint.setShader(null);
            fillPaint.setColor(readable);
            fillPaint.setAlpha((int) (255 * opacityK));
            drawable.draw(canvas, fillPaint);
        }
        return true;
    }

    /** Moves the bubble color towards black or white until the viewer's text stays readable on it. */
    static int readable(int bubble, int textColor) {
        final int text = textColor | 0xFF000000;
        if (ColorUtils.calculateContrast(text, bubble) >= MIN_TEXT_CONTRAST) {
            return bubble;
        }
        final int target = ColorUtils.calculateLuminance(text) > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
        int result = bubble;
        for (int i = 1; i <= 10; i++) {
            result = ColorUtils.blendARGB(bubble, target, i * 0.08f);
            if (ColorUtils.calculateContrast(text, result) >= MIN_TEXT_CONTRAST) {
                break;
            }
        }
        return result;
    }
}
