package org.telegram.othergram.theme;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.othergram.OtherGramConfig;

/**
 * Avatar shape for users and regular groups. Telegram draws avatars as images clipped with a corner
 * radius of half their size; the chosen shape scales that radius, so one change in ImageReceiver and
 * AvatarDrawable covers chat lists, chats and profiles. Forums keep Telegram's own rounded squares.
 */
public class OtherGramAvatars {

    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_SQUIRCLE = 1;
    public static final int SHAPE_ROUNDED = 2;
    public static final int SHAPE_SQUARE = 3;

    public static final int[] SHAPES = {SHAPE_CIRCLE, SHAPE_SQUIRCLE, SHAPE_ROUNDED, SHAPE_SQUARE};
    public static final int[] SHAPE_TITLES = {R.string.OG_AvatarCircle, R.string.OG_AvatarSquircle, R.string.OG_AvatarRounded, R.string.OG_AvatarSquare};

    private static final String KEY_SHAPE = "avatar_shape";
    private static volatile int shape = -1;

    public static int getShape() {
        if (shape < 0) {
            shape = OtherGramConfig.getInt(KEY_SHAPE, SHAPE_CIRCLE);
        }
        return shape;
    }

    public static void setShape(int value) {
        shape = value;
        OtherGramConfig.putInt(KEY_SHAPE, value);
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.isValidAccount(a)) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                }
            }
        });
    }

    /** Share of the circle radius kept by the shape. */
    public static float radiusFactor() {
        switch (getShape()) {
            case SHAPE_SQUIRCLE:
                return 0.62f;
            case SHAPE_ROUNDED:
                return 0.36f;
            case SHAPE_SQUARE:
                return 0f;
            default:
                return 1f;
        }
    }

    public static boolean isCircle() {
        return getShape() == SHAPE_CIRCLE;
    }

    /** Radius actually used for an avatar that callers asked to round with {@code requested}. */
    public static int adjust(int requested) {
        return isCircle() ? requested : Math.round(requested * radiusFactor());
    }
}
