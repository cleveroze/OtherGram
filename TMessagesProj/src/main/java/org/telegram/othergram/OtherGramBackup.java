package org.telegram.othergram;

import android.app.Activity;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.othergram.chats.OtherGramBubbles;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.othergram.theme.OtherGramFonts;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Export and import of OtherGram settings as a small ".othergram" file (JSON).
 *
 * Two kinds of files: a full backup (every setting, including privacy) for moving to another phone
 * or a new OtherGram version, and a shared look (appearance only) to give your style to friends.
 * Server tokens, admin flags, the Telegram session, messages and phone numbers are never written,
 * and importing only accepts settings from the allowed groups, so a file from someone else cannot
 * sign you in anywhere or change anything except the look.
 */
public class OtherGramBackup {

    public static final String EXTENSION = ".othergram";
    private static final String FORMAT = "othergram-settings";
    private static final int FORMAT_VERSION = 1;
    private static final String MODE_FULL = "full";
    private static final String MODE_APPEARANCE = "appearance";
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_EMBEDDED_FONTS_BYTES = 8L * 1024 * 1024;

    // ------------------------------------------------------------ key groups

    /** Never exported or imported: sign-in data and admin flags. */
    private static boolean isBlocked(String key) {
        final String k = key.toLowerCase(Locale.US);
        return k.startsWith("server_") || k.contains("token") || k.contains("secret") || k.contains("admin") || k.contains("session");
    }

    /** Privacy settings travel only in a full backup, never in a shared look. */
    private static boolean isPrivacy(String key) {
        return key.startsWith("ghost_") || key.startsWith("keep_deleted");
    }

    private static boolean allowed(String key, boolean appearanceOnly) {
        return !isBlocked(key) && !(appearanceOnly && isPrivacy(key));
    }

    // ------------------------------------------------------------ export

    /** Builds the file contents. {@code appearanceOnly} leaves privacy settings out. */
    public static byte[] export(boolean appearanceOnly) throws Exception {
        final JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("app", OtherGramConfig.VERSION);
        root.put("mode", appearanceOnly ? MODE_APPEARANCE : MODE_FULL);
        root.put("created", System.currentTimeMillis() / 1000);

        final JSONObject settings = new JSONObject();
        final ArrayList<String> customFonts = new ArrayList<>();
        for (Map.Entry<String, ?> e : OtherGramConfig.getAll().entrySet()) {
            final String key = e.getKey();
            final Object v = e.getValue();
            if (!allowed(key, appearanceOnly) || v == null) {
                continue;
            }
            final JSONObject item = new JSONObject();
            if (v instanceof Boolean) {
                item.put("t", "b");
            } else if (v instanceof Integer) {
                item.put("t", "i");
            } else if (v instanceof Long) {
                item.put("t", "l");
            } else if (v instanceof Float) {
                item.put("t", "f");
            } else if (v instanceof String) {
                item.put("t", "s");
                if (key.startsWith("font_") && ((String) v).startsWith(OtherGramFonts.FILE_PREFIX)) {
                    customFonts.add((String) v);
                }
            } else {
                continue;
            }
            item.put("v", v instanceof Float ? (double) (Float) v : v);
            settings.put(key, item);
        }
        root.put("settings", settings);

        // Imported fonts in use travel inside the file, so the look stays the same on another phone
        final JSONObject fonts = new JSONObject();
        long total = 0;
        for (String font : customFonts) {
            final File file = OtherGramFonts.customFontFile(font);
            if (file == null || fonts.has(file.getName()) || total + file.length() > MAX_EMBEDDED_FONTS_BYTES) {
                continue;
            }
            fonts.put(file.getName(), Base64.encodeToString(readAll(new FileInputStream(file), file.length() + 1), Base64.NO_WRAP));
            total += file.length();
        }
        if (fonts.length() > 0) {
            root.put("fonts", fonts);
        }
        return root.toString(1).getBytes(StandardCharsets.UTF_8);
    }

    public static String fileName(boolean appearanceOnly) {
        final String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return (appearanceOnly ? "OtherGram-style-" : "OtherGram-backup-") + date + EXTENSION;
    }

    /** Writes an export into the app's shareable cache folder and returns the file. */
    public static File exportToCacheFile(boolean appearanceOnly) throws Exception {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        final File file = new File(dir, fileName(appearanceOnly));
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(export(appearanceOnly));
        }
        return file;
    }

    // ------------------------------------------------------------ import

    /** A parsed, checked settings file, ready to apply. */
    public static class Parsed {
        public final boolean appearanceOnly;
        public final String appVersion;
        public final long created;
        final HashMap<String, Object> values = new HashMap<>();
        final HashMap<String, byte[]> fonts = new HashMap<>();
        public int colorCount, fontCount, otherCount, privacyCount;

        Parsed(boolean appearanceOnly, String appVersion, long created) {
            this.appearanceOnly = appearanceOnly;
            this.appVersion = appVersion;
            this.created = created;
        }
    }

    /** Reads and checks a file. Returns null when it is not an OtherGram settings file. */
    public static Parsed parse(byte[] data) {
        try {
            final JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
            if (!FORMAT.equals(root.optString("format")) || root.optInt("version", 0) < 1) {
                return null;
            }
            final boolean appearanceOnly = !MODE_FULL.equals(root.optString("mode"));
            final Parsed parsed = new Parsed(appearanceOnly, root.optString("app", "?"), root.optLong("created", 0));
            final JSONObject settings = root.optJSONObject("settings");
            if (settings == null) {
                return null;
            }
            final Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                if (!allowed(key, appearanceOnly) || key.length() > 100) {
                    continue;
                }
                final JSONObject item = settings.optJSONObject(key);
                if (item == null || !item.has("v")) {
                    continue;
                }
                final Object value;
                switch (item.optString("t")) {
                    case "b": value = item.getBoolean("v"); break;
                    case "i": value = item.getInt("v"); break;
                    case "l": value = item.getLong("v"); break;
                    case "f": value = (float) item.getDouble("v"); break;
                    case "s": {
                        final String s = item.getString("v");
                        if (s.length() > 2000) continue;
                        value = s;
                        break;
                    }
                    default: continue;
                }
                parsed.values.put(key, value);
                if (key.startsWith("color_")) parsed.colorCount++;
                else if (key.startsWith("font_")) parsed.fontCount++;
                else if (isPrivacy(key)) parsed.privacyCount++;
                else parsed.otherCount++;
            }
            final JSONObject fonts = root.optJSONObject("fonts");
            if (fonts != null) {
                final Iterator<String> names = fonts.keys();
                while (names.hasNext()) {
                    final String name = names.next();
                    try {
                        parsed.fonts.put(name, Base64.decode(fonts.getString(name), Base64.DEFAULT));
                    } catch (Exception ignore) {
                    }
                }
            }
            return parsed;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Applies a parsed file so the result matches it exactly: settings of the same kind that the file
     * does not have are reset. The current settings are saved first, so "Undo" can bring them back.
     */
    public static void apply(Parsed parsed) {
        try {
            saveRestorePoint();
        } catch (Exception e) {
            FileLog.e(e);
        }
        for (Map.Entry<String, byte[]> font : parsed.fonts.entrySet()) {
            OtherGramFonts.installFont(font.getKey(), font.getValue());
        }
        final ArrayList<String> toRemove = new ArrayList<>();
        for (String key : OtherGramConfig.getAll().keySet()) {
            if (allowed(key, parsed.appearanceOnly) && !parsed.values.containsKey(key)) {
                toRemove.add(key);
            }
        }
        OtherGramConfig.replace(toRemove, parsed.values);
        Theme.refreshThemeColors();
        publishBubbleStyle();
    }

    /** Own bubble style is public, so after an import it is sent to the server like a manual change. */
    private static void publishBubbleStyle() {
        if (!OtherGramApi.isLoggedIn()) {
            return;
        }
        try {
            final JSONObject changes = new JSONObject();
            final String style = OtherGramBubbles.myStyle();
            changes.put("bubble_style", style == null ? JSONObject.NULL : style);
            changes.put("bubble_color", OtherGramBubbles.myColor());
            changes.put("bubble_alpha", OtherGramBubbles.myAlpha());
            OtherGramProfiles.updateMyProfile(changes, (status, me) -> { });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ------------------------------------------------------------ undo

    private static File restoreFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "othergram_restore" + EXTENSION);
    }

    private static void saveRestorePoint() throws Exception {
        try (FileOutputStream out = new FileOutputStream(restoreFile())) {
            out.write(export(false));
        }
    }

    public static boolean hasRestorePoint() {
        return restoreFile().exists();
    }

    /** Brings back the settings that were active before the last import. */
    public static boolean undo() {
        final File file = restoreFile();
        if (!file.exists()) {
            return false;
        }
        try {
            final Parsed parsed = parse(readAll(new FileInputStream(file), MAX_FILE_BYTES));
            if (parsed == null) {
                return false;
            }
            for (Map.Entry<String, byte[]> font : parsed.fonts.entrySet()) {
                OtherGramFonts.installFont(font.getKey(), font.getValue());
            }
            final ArrayList<String> toRemove = new ArrayList<>();
            for (String key : OtherGramConfig.getAll().keySet()) {
                if (allowed(key, false) && !parsed.values.containsKey(key)) {
                    toRemove.add(key);
                }
            }
            OtherGramConfig.replace(toRemove, parsed.values);
            file.delete();
            Theme.refreshThemeColors();
            publishBubbleStyle();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    // ------------------------------------------------------------ UI helpers

    public static byte[] readAll(InputStream in, long limit) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[16384];
            long total = 0;
            int n;
            while ((n = input.read(buffer)) > 0) {
                total += n;
                if (total > limit) {
                    throw new IllegalArgumentException("file too large");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    public static long maxFileBytes() {
        return MAX_FILE_BYTES;
    }

    /** Opened from a chat: a tap on a ".othergram" file offers to apply it. */
    public static boolean openFromChat(Activity activity, File file) {
        if (activity == null || file == null || !file.exists()) {
            return false;
        }
        try {
            showImportDialog(activity, readAll(new FileInputStream(file), MAX_FILE_BYTES), null);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    /** Shows what the file contains and applies it after confirmation. */
    public static void showImportDialog(Activity activity, byte[] data, Runnable onApplied) {
        final Parsed parsed = parse(data);
        if (parsed == null) {
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.OG_ImportTitle))
                    .setMessage(LocaleController.getString(R.string.OG_ImportBadFile))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return;
        }
        final StringBuilder message = new StringBuilder();
        message.append(LocaleController.getString(parsed.appearanceOnly ? R.string.OG_ImportKindStyle : R.string.OG_ImportKindBackup));
        if (parsed.created > 0) {
            message.append("\n").append(LocaleController.formatString(R.string.OG_ImportCreated,
                    LocaleController.getInstance().getFormatterDayMonth().format(parsed.created * 1000), parsed.appVersion));
        }
        message.append("\n\n").append(LocaleController.formatString(R.string.OG_ImportContents,
                parsed.colorCount, parsed.fontCount, parsed.otherCount + parsed.privacyCount));
        if (!parsed.fonts.isEmpty()) {
            message.append("\n").append(LocaleController.formatString(R.string.OG_ImportFonts, parsed.fonts.size()));
        }
        message.append("\n\n").append(LocaleController.getString(R.string.OG_ImportWarning));
        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.OG_ImportTitle))
                .setMessage(message.toString())
                .setPositiveButton(LocaleController.getString(R.string.OG_ImportApply), (d, w) -> {
                    apply(parsed);
                    if (onApplied != null) {
                        onApplied.run();
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(LocaleController.getString(R.string.OG_ImportDoneTitle))
                            .setMessage(LocaleController.getString(R.string.OG_ImportDone))
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
