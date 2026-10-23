package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.othergram.theme.OtherGramFonts;

/**
 * Banner at the top of OtherGram Settings: the OtherGram artwork with rounded corners (same radius
 * as the settings cards) and the app version written over its free bottom part.
 */
public class OtherGramBannerHeader extends View {

    private static final float ASPECT = 168f / 400f;
    private static final int SIDE_PADDING_DP = 12; // same as the list's section cards
    private static final int RADIUS_DP = 16;
    private static final int VERSION_COLOR = 0xD0155641; // the dark green of the "Other" lettering

    private final Bitmap bitmap;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint versionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String version;
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Path clip = new Path();

    public OtherGramBannerHeader(Context context) {
        super(context);
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeResource(getResources(), R.drawable.og_settings_banner);
        } catch (Throwable ignore) {
        }
        bitmap = b;
        if (bitmap != null) {
            src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }
        Typeface typeface = OtherGramFonts.preview("manrope", true);
        versionPaint.setTypeface(typeface != null ? typeface : AndroidUtilities.bold());
        versionPaint.setColor(VERSION_COLOR);
        versionPaint.setTextAlign(Paint.Align.CENTER);
        versionPaint.setLetterSpacing(0.04f);
        version = LocaleController.formatString(R.string.OG_VersionLabel, OtherGramConfig.VERSION);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int bannerHeight = Math.round((width - dp(SIDE_PADDING_DP) * 2) * ASPECT);
        setMeasuredDimension(width, bannerHeight + dp(8) + dp(4));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(dp(SIDE_PADDING_DP), dp(8), w - dp(SIDE_PADDING_DP), h - dp(4));
        clip.reset();
        clip.addRoundRect(rect, dp(RADIUS_DP), dp(RADIUS_DP), Path.Direction.CW);
        // The artwork is 400 wide; scale the version text with the banner so it keeps its place
        versionPaint.setTextSize(Math.max(dp(10), rect.width() * 0.034f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null) {
            return;
        }
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawBitmap(bitmap, src, rect, bitmapPaint);
        canvas.restore();
        // Free band under the logo: the text sits in its middle
        final float baseline = rect.top + rect.height() * 0.905f;
        canvas.drawText(version, rect.centerX(), baseline, versionPaint);
    }
}
