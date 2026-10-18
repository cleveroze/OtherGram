package org.telegram.othergram.theme;

import android.graphics.Typeface;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;

import java.util.HashMap;

/**
 * Bundled OFL fonts (assets/fonts/othergram, licenses next to them). Headings/names and message text
 * can use different fonts. Telegram caches its paints, so a change applies after an app restart.
 */
public class OtherGramFonts {

    public static final String SYSTEM = "system";
    public static final String[] FONTS = {SYSTEM, "manrope", "nunito", "comfortaa", "lora"};
    public static final int[] FONT_TITLES = {R.string.OG_FontSystem, R.string.OG_FontManrope, R.string.OG_FontNunito, R.string.OG_FontComfortaa, R.string.OG_FontLora};

    public static final String TARGET_HEADINGS = "font_headings";
    public static final String TARGET_MESSAGES = "font_messages";
    public static final String TARGET_INTERFACE = "font_interface";

    private static final int HEADING_WEIGHT = 650;
    private static final HashMap<String, Typeface> cache = new HashMap<>();

    public static String get(String target) {
        return OtherGramConfig.getString(target, SYSTEM);
    }

    public static void set(String target, String font) {
        OtherGramConfig.putString(target, font);
    }

    public static int titleRes(String font) {
        for (int i = 0; i < FONTS.length; i++) {
            if (FONTS[i].equals(font)) {
                return FONT_TITLES[i];
            }
        }
        return FONT_TITLES[0];
    }

    /** Font for bold text (headings, names), or null to keep Telegram's own. */
    public static Typeface headings() {
        return load(get(TARGET_HEADINGS), true);
    }

    /** Font for message text, or null for the system font. */
    public static Typeface messages() {
        return load(get(TARGET_MESSAGES), false);
    }

    /** Font for regular interface text such as chat list previews, or null for the system font. */
    public static Typeface regular() {
        return load(get(TARGET_INTERFACE), false);
    }

    public static Typeface preview(String font, boolean heading) {
        return load(font, heading);
    }

    private static synchronized Typeface load(String font, boolean heading) {
        if (font == null || SYSTEM.equals(font)) {
            return null;
        }
        final String cacheKey = font + (heading ? ":h" : ":r");
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        Typeface typeface = null;
        final String path = "fonts/othergram/" + font + ".ttf";
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // The bundled files are variable fonts: pick the weight through the wght axis
                typeface = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), path)
                        .setFontVariationSettings("'wght' " + (heading ? HEADING_WEIGHT : 400))
                        .setWeight(heading ? 700 : 400)
                        .build();
            } else {
                final Typeface base = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), path);
                typeface = heading ? Typeface.create(base, Typeface.BOLD) : base;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        cache.put(cacheKey, typeface);
        return typeface;
    }
}
