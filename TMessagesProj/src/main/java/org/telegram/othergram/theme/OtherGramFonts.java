package org.telegram.othergram.theme;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Fonts for headings/names, message text and interface text: bundled OFL fonts
 * (assets/fonts/othergram, licenses next to them) and TTF/OTF files the user imported
 * (stored in the app's private files as "file:&lt;name&gt;"). Telegram caches its paints, so a change
 * applies after an app restart.
 */
public class OtherGramFonts {

    public static final String SYSTEM = "system";
    public static final String FILE_PREFIX = "file:";
    private static final String[] BUILT_IN = {SYSTEM, "manrope", "nunito", "comfortaa", "lora"};
    private static final int[] BUILT_IN_TITLES = {R.string.OG_FontSystem, R.string.OG_FontManrope, R.string.OG_FontNunito, R.string.OG_FontComfortaa, R.string.OG_FontLora};

    public static final String TARGET_HEADINGS = "font_headings";
    public static final String TARGET_MESSAGES = "font_messages";
    public static final String TARGET_INTERFACE = "font_interface";

    private static final int HEADING_WEIGHT = 650;
    private static final long MAX_FONT_BYTES = 15L * 1024 * 1024;
    private static final HashMap<String, Typeface> cache = new HashMap<>();

    public static String get(String target) {
        return OtherGramConfig.getString(target, SYSTEM);
    }

    public static void set(String target, String font) {
        OtherGramConfig.putString(target, font);
    }

    /** Built-in fonts followed by imported ones. */
    public static List<String> allFonts() {
        final ArrayList<String> list = new ArrayList<>(Arrays.asList(BUILT_IN));
        list.addAll(customFonts());
        return list;
    }

    public static List<String> customFonts() {
        final ArrayList<String> list = new ArrayList<>();
        final File[] files = customDir().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                list.add(FILE_PREFIX + f.getName());
            }
        }
        return list;
    }

    public static String title(Context context, String font) {
        for (int i = 0; i < BUILT_IN.length; i++) {
            if (BUILT_IN[i].equals(font)) {
                return context.getString(BUILT_IN_TITLES[i]);
            }
        }
        if (font != null && font.startsWith(FILE_PREFIX)) {
            final String name = font.substring(FILE_PREFIX.length());
            final int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        return context.getString(R.string.OG_FontSystem);
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

    private static File customDir() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "othergram_fonts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
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
        try {
            if (font.startsWith(FILE_PREFIX)) {
                final File file = new File(customDir(), font.substring(FILE_PREFIX.length()));
                if (file.exists()) {
                    final Typeface base = Typeface.createFromFile(file);
                    typeface = heading ? Typeface.create(base, Typeface.BOLD) : base;
                }
            } else {
                final String path = "fonts/othergram/" + font + ".ttf";
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
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        cache.put(cacheKey, typeface);
        return typeface;
    }

    /**
     * Copies a TTF/OTF file the user picked into private storage after checking that Android can
     * load it. Returns the font id, or null when the file is not a usable font. Runs on a worker thread.
     */
    public static String importFont(Context context, Uri uri) {
        String name = displayName(context, uri);
        final String lower = name.toLowerCase(Locale.US);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
            name = name + ".ttf";
        }
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        final File target = new File(customDir(), name);
        final File tmp = new File(customDir(), name + ".tmp");
        try (InputStream in = context.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                return null;
            }
            final byte[] buffer = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_FONT_BYTES) {
                    throw new IllegalArgumentException("font file too large");
                }
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            FileLog.e(e);
            tmp.delete();
            return null;
        }
        try {
            final Typeface probe = Typeface.createFromFile(tmp);
            if (probe == null) {
                tmp.delete();
                return null;
            }
        } catch (Exception e) {
            tmp.delete();
            return null;
        }
        if (target.exists()) {
            target.delete();
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            return null;
        }
        synchronized (OtherGramFonts.class) {
            cache.remove(FILE_PREFIX + name + ":h");
            cache.remove(FILE_PREFIX + name + ":r");
        }
        return FILE_PREFIX + name;
    }

    /** Deletes an imported font; targets using it fall back to the system font. */
    public static void deleteCustom(String font) {
        if (font == null || !font.startsWith(FILE_PREFIX)) {
            return;
        }
        new File(customDir(), font.substring(FILE_PREFIX.length())).delete();
        for (String target : new String[]{TARGET_HEADINGS, TARGET_MESSAGES, TARGET_INTERFACE}) {
            if (font.equals(get(target))) {
                set(target, SYSTEM);
            }
        }
        synchronized (OtherGramFonts.class) {
            cache.remove(font + ":h");
            cache.remove(font + ":r");
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                final String name = c.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "font_" + System.currentTimeMillis() + ".ttf";
    }
}
