package org.telegram.othergram.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installed plugins: ".plugin" files in the exteraGram format (a Python file with __id__, __name__,
 * __version__ ... metadata). Files are stored in the app's private folder; each can be switched off
 * or deleted. Installing requires the user's consent that plugins may be harmful or break OtherGram.
 */
public class OtherGramPlugins {

    public static final String EXTENSION = ".plugin";
    private static final long MAX_PLUGIN_BYTES = 2L * 1024 * 1024;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    public static class Plugin {
        public String id;
        public String name;
        public String description;
        public String author;
        public String version;
        public String minVersion;
        public File file;
        public boolean enabled;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("othergram_plugins", Context.MODE_PRIVATE);
    }

    private static File dir() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static boolean hasConsent() {
        return prefs().getBoolean("consent", false);
    }

    public static void setConsent(boolean consent) {
        prefs().edit().putBoolean("consent", consent).apply();
    }

    /** Reads plugin metadata; returns null when the text is not a plugin. */
    public static Plugin parse(String source) {
        if (source == null) {
            return null;
        }
        final Plugin p = new Plugin();
        p.id = meta(source, "__id__");
        p.name = meta(source, "__name__");
        if (p.id == null || p.name == null || !ID_PATTERN.matcher(p.id).matches()) {
            return null;
        }
        p.description = meta(source, "__description__");
        p.author = meta(source, "__author__");
        p.version = meta(source, "__version__");
        p.minVersion = meta(source, "__min_version__");
        if (p.minVersion == null) {
            p.minVersion = meta(source, "__app_version__");
        }
        return p;
    }

    private static String meta(String source, String key) {
        final Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(?:\"\"\"([\\s\\S]*?)\"\"\"|\"([^\"\\n]*)\"|'([^'\\n]*)')").matcher(source);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        value = value == null ? null : value.trim();
        return value == null || value.isEmpty() ? null : value;
    }

    public static Plugin parseBytes(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_PLUGIN_BYTES) {
            return null;
        }
        return parse(new String(data, StandardCharsets.UTF_8));
    }

    /** Saves a plugin (replacing an older version with the same id); new plugins start switched off. */
    public static Plugin install(byte[] data) {
        final Plugin p = parseBytes(data);
        if (p == null) {
            return null;
        }
        final File target = new File(dir(), p.id + EXTENSION);
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(data);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        p.file = target;
        p.enabled = isEnabled(p.id);
        return p;
    }

    public static List<Plugin> list() {
        final ArrayList<Plugin> result = new ArrayList<>();
        final File[] files = dir().listFiles((d, name) -> name.endsWith(EXTENSION));
        if (files == null) {
            return result;
        }
        for (File f : files) {
            try (FileInputStream in = new FileInputStream(f)) {
                final byte[] data = new byte[(int) Math.min(f.length(), MAX_PLUGIN_BYTES)];
                int read = 0;
                while (read < data.length) {
                    final int n = in.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
                final Plugin p = parseBytes(data);
                if (p != null) {
                    p.file = f;
                    p.enabled = isEnabled(p.id);
                    result.add(p);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    public static boolean isEnabled(String id) {
        return prefs().getBoolean("enabled_" + id, false);
    }

    public static void setEnabled(String id, boolean enabled) {
        prefs().edit().putBoolean("enabled_" + id, enabled).apply();
    }

    /** A ".plugin" file tapped in a chat: show what it is and the risks, install after confirmation. */
    public static boolean openFromChat(android.app.Activity activity, File file) {
        if (activity == null || file == null || !file.exists() || file.length() > MAX_PLUGIN_BYTES) {
            return false;
        }
        final byte[] data;
        try (FileInputStream in = new FileInputStream(file)) {
            data = org.telegram.othergram.OtherGramBackup.readAll(in, MAX_PLUGIN_BYTES);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        final Plugin plugin = parseBytes(data);
        if (plugin == null) {
            return false;
        }
        final String message = org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OG_PluginsConsent);
        new org.telegram.ui.ActionBar.AlertDialog.Builder(activity)
                .setTitle(plugin.name + (plugin.version != null ? " " + plugin.version : ""))
                .setMessage(message)
                .setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OG_PluginsAgree), (d, w) -> {
                    setConsent(true);
                    final Plugin installed = install(data);
                    android.widget.Toast.makeText(activity, installed != null
                            ? org.telegram.messenger.LocaleController.formatString(org.telegram.messenger.R.string.OG_PluginInstalled, installed.name)
                            : org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OG_PluginBadFile), android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null)
                .show();
        return true;
    }

    public static void delete(Plugin plugin) {
        if (plugin.file != null) {
            plugin.file.delete();
        }
        prefs().edit().remove("enabled_" + plugin.id).apply();
    }
}
