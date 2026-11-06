package org.telegram.othergram.banners;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.ui.ActionBar.Theme;

/**
 * OtherGram profile background: a picture under the profile info rows, from the phone number down to
 * the media tabs ("Posts"). It is drawn under the rows (over the section cards), tinted with the
 * theme background color so the row texts stay readable in light and dark themes, and fades in and
 * out softly at its top and bottom. The picture stays fixed while the list scrolls over it.
 */
public class OtherGramProfileBackground extends RecyclerView.ItemDecoration {

    public interface Rows {
        long userId();
        /** First row with the background (the phone row or the first info row), or -1. */
        int startRow();
        /** Last info row, used when there is no media section. */
        int endRow();
        /** The shared media row (its tabs end the background), or -1. */
        int mediaRow();
    }

    private static final int SIDE_DP = 12;
    private static final int RADIUS_DP = 16;
    private static final int FADE_DP = 36;
    private static final int MEDIA_TABS_DP = 48;

    private final RecyclerView list;
    private final Rows rows;
    private final ImageReceiver image;
    private final Paint tint = new Paint();
    private final Paint fadeTop = new Paint();
    private final Paint fadeBottom = new Paint();
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private String loadedUrl;
    private boolean attached;

    public OtherGramProfileBackground(RecyclerView list, Rows rows) {
        this.list = list;
        this.rows = rows;
        image = new ImageReceiver(list);
        image.setCrossfadeDuration(300);
        list.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                image.onAttachedToWindow();
                attached = true;
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                image.onDetachedFromWindow();
                attached = false;
            }
        });
        if (list.isAttachedToWindow()) {
            image.onAttachedToWindow();
            attached = true;
        }
    }

    /** How much theme color covers the picture: the owner's 0..80 setting maps to 30..80 % (the picture stays visible). */
    public static float tintStrength(int dim) {
        return Math.min(0.8f, 0.3f + Math.max(0, dim) / 160f);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        final OtherGramProfiles.Profile profile = rows.userId() > 0 ? OtherGramProfiles.getProfile(rows.userId()) : null;
        final int start = rows.startRow();
        if (profile == null || profile.backgroundUrl == null || start < 0) {
            return;
        }
        if (!profile.backgroundUrl.equals(loadedUrl)) {
            loadedUrl = profile.backgroundUrl;
            image.setImage(ImageLocation.getForPath(loadedUrl), (String) null, (Drawable) null, (String) null, (Object) null, 1);
        }

        // 2.1: the background covers the whole profile, not just the info rows — it runs down to the
        // last row. It is drawn under the rows, so the banner and the texts stay on top of it.
        final int media = rows.mediaRow();
        final int end = parent.getAdapter() != null ? parent.getAdapter().getItemCount() - 1 : rows.endRow();
        float top = Float.NaN;
        float bottom = Float.NaN;
        int firstPos = Integer.MAX_VALUE;
        int lastPos = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            final int pos = parent.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) {
                continue;
            }
            firstPos = Math.min(firstPos, pos);
            lastPos = Math.max(lastPos, pos);
            final float childTop = child.getY();
            if (pos == start) {
                top = childTop;
            }
            if (pos == end) {
                bottom = pos == media ? childTop + dp(MEDIA_TABS_DP) : childTop + child.getHeight();
            }
        }
        if (lastPos < 0 || lastPos < start || firstPos > end) {
            return; // the region is not on screen
        }
        final boolean topVisible = !Float.isNaN(top);
        final boolean bottomVisible = !Float.isNaN(bottom);
        if (!topVisible) top = -dp(FADE_DP);
        if (!bottomVisible) bottom = parent.getHeight() + dp(FADE_DP);
        if (bottom <= top) {
            return;
        }

        final float left = dp(SIDE_DP);
        final float right = parent.getWidth() - dp(SIDE_DP);
        rect.set(left, top, right, bottom);
        clip.reset();
        clip.addRoundRect(rect, dp(RADIUS_DP), dp(RADIUS_DP), Path.Direction.CW);

        c.save();
        c.clipPath(clip);
        // Fixed to the screen (cover), so the rows slide over a still picture
        if (image.hasImageLoaded() && image.getBitmapWidth() > 0) {
            final float scale = Math.max(parent.getWidth() / (float) image.getBitmapWidth(), parent.getHeight() / (float) image.getBitmapHeight());
            final float w = image.getBitmapWidth() * scale;
            final float h = image.getBitmapHeight() * scale;
            image.setImageCoords((parent.getWidth() - w) / 2f, (parent.getHeight() - h) / 2f, w, h);
            image.draw(c);
        }
        // Theme-colored tint: darkens in the dark theme, lightens in the light one, so texts stay readable
        final int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        final float strength = tintStrength(profile.backgroundDim);
        tint.setColor(Theme.multAlpha(bg | 0xFF000000, strength));
        c.drawRect(rect, tint);
        // Soft edges: the picture fades in under the first row and out at the media tabs
        final float fade = dp(FADE_DP);
        if (topVisible) {
            fadeTop.setShader(new LinearGradient(0, top, 0, top + fade, bg | 0xFF000000, bg & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(left, top, right, top + fade, fadeTop);
        }
        if (bottomVisible) {
            fadeBottom.setShader(new LinearGradient(0, bottom - fade, 0, bottom, bg & 0x00FFFFFF, bg | 0xFF000000, Shader.TileMode.CLAMP));
            c.drawRect(left, bottom - fade, right, bottom, fadeBottom);
        }
        c.restore();
    }
}
