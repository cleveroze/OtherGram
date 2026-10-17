package org.telegram.othergram.banners;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Draws a user's OtherGram banner (image or solid color) at the top of the profile header.
 * The lower part fades into the header color so the name and status stay readable in any theme.
 */
public class OtherGramBannerDrawer {

    private static final float FADE_START = 0.45f;
    private static final int APPEAR_MS = 420;

    private final View parent;
    private final ImageReceiver imageReceiver;
    private final AnimatedFloat appear;
    private final Paint colorPaint = new Paint();
    private final Paint fadePaint = new Paint();
    private String loadedUrl;
    private int fadeColor;
    private float fadeHeight;

    public OtherGramBannerDrawer(View parent) {
        this.parent = parent;
        imageReceiver = new ImageReceiver(parent);
        imageReceiver.setCrossfadeDuration(APPEAR_MS);
        appear = new AnimatedFloat(parent, 0, APPEAR_MS, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void onAttachedToWindow() {
        imageReceiver.onAttachedToWindow();
    }

    public void onDetachedFromWindow() {
        imageReceiver.onDetachedFromWindow();
    }

    /**
     * @param height     current visible header height
     * @param fadeTo     header background color to fade into
     */
    public void draw(Canvas canvas, long userId, int width, float height, int fadeTo) {
        final OtherGramProfiles.Profile profile = userId > 0 ? OtherGramProfiles.getProfile(userId) : null;
        final String kind = profile != null ? profile.bannerKind : null;
        final float alpha = appear.set(kind != null);
        if (alpha <= 0 || height <= 0) {
            return;
        }
        if (OtherGramProfiles.Profile.BANNER_IMAGE.equals(kind)) {
            if (!profile.bannerUrl.equals(loadedUrl)) {
                loadedUrl = profile.bannerUrl;
                imageReceiver.setImage(ImageLocation.getForPath(loadedUrl), (String) null, (Drawable) null, (String) null, (Object) null, 1);
            }
            drawImage(canvas, width, height, profile.bannerOffset, alpha);
        } else if (OtherGramProfiles.Profile.BANNER_COLOR.equals(kind)) {
            colorPaint.setColor(profile.bannerColor);
            colorPaint.setAlpha((int) (255 * alpha));
            canvas.drawRect(0, 0, width, height, colorPaint);
        }
        drawFade(canvas, width, height, fadeTo, alpha);
    }

    private void drawImage(Canvas canvas, int width, float height, float offset, float alpha) {
        if (!imageReceiver.hasImageLoaded() || imageReceiver.getBitmapWidth() <= 0) {
            return;
        }
        // Scale to fill the header width; the stored offset picks which part of a tall image is shown
        final float scale = Math.max(width / (float) imageReceiver.getBitmapWidth(), height / (float) imageReceiver.getBitmapHeight());
        final float drawW = imageReceiver.getBitmapWidth() * scale;
        final float drawH = imageReceiver.getBitmapHeight() * scale;
        final float x = (width - drawW) / 2f;
        final float y = -(drawH - height) * Math.max(0f, Math.min(1f, offset));
        canvas.save();
        canvas.clipRect(0, 0, width, height);
        imageReceiver.setImageCoords(x, y, drawW, drawH);
        imageReceiver.setAlpha(alpha);
        imageReceiver.draw(canvas);
        canvas.restore();
    }

    private void drawFade(Canvas canvas, int width, float height, int fadeTo, float alpha) {
        if (fadeColor != fadeTo || fadeHeight != height) {
            fadeColor = fadeTo;
            fadeHeight = height;
            fadePaint.setShader(new LinearGradient(0, height * FADE_START, 0, height,
                    new int[]{fadeTo & 0x00FFFFFF, fadeTo}, null, Shader.TileMode.CLAMP));
        }
        fadePaint.setAlpha((int) (255 * alpha));
        canvas.drawRect(0, height * FADE_START, width, height, fadePaint);
    }
}
