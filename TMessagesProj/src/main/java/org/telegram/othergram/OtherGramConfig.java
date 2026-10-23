package org.telegram.othergram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Storage for all OtherGram settings. Kept in its own SharedPreferences file so that
 * Telegram's configs stay untouched and "restore default design" is a single clear().
 *
 * Themed values are stored separately for light and dark themes: key + "_light" / "_dark".
 */
public class OtherGramConfig {

    public interface Listener {
        /** @param key changed key (without the theme suffix), or null when everything was reset */
        void onOtherGramConfigChanged(String key);
    }

    /** OtherGram version shown in the settings banner and written into exported settings. */
    public static final String VERSION = "1.3.0";

    private static final String PREFS_NAME = "othergram_config";
    private static final String SUFFIX_LIGHT = "_light";
    private static final String SUFFIX_DARK = "_dark";

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile SharedPreferences preferences;

    private static SharedPreferences prefs() {
        SharedPreferences p = preferences;
        if (p == null) {
            synchronized (OtherGramConfig.class) {
                p = preferences;
                if (p == null) {
                    preferences = p = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                }
            }
        }
        return p;
    }

    public static String themedKey(String key, boolean dark) {
        return key + (dark ? SUFFIX_DARK : SUFFIX_LIGHT);
    }

    private static String currentThemedKey(String key) {
        return themedKey(key, Theme.isCurrentThemeDark());
    }

    public static boolean contains(String key) {
        return prefs().contains(key);
    }

    public static boolean getBoolean(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public static int getInt(String key, int def) {
        return prefs().getInt(key, def);
    }

    public static float getFloat(String key, float def) {
        return prefs().getFloat(key, def);
    }

    public static String getString(String key, String def) {
        return prefs().getString(key, def);
    }

    public static void putBoolean(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
        notifyChanged(key);
    }

    public static void putInt(String key, int value) {
        prefs().edit().putInt(key, value).apply();
        notifyChanged(key);
    }

    public static void putFloat(String key, float value) {
        prefs().edit().putFloat(key, value).apply();
        notifyChanged(key);
    }

    public static void putString(String key, String value) {
        prefs().edit().putString(key, value).apply();
        notifyChanged(key);
    }

    public static void remove(String key) {
        prefs().edit().remove(key).apply();
        notifyChanged(key);
    }

    /** Value for the currently active (light or dark) theme. */
    public static int getThemedInt(String key, int def) {
        return prefs().getInt(currentThemedKey(key), def);
    }

    public static int getThemedInt(String key, boolean dark, int def) {
        return prefs().getInt(themedKey(key, dark), def);
    }

    public static void putThemedInt(String key, boolean dark, int value) {
        prefs().edit().putInt(themedKey(key, dark), value).apply();
        notifyChanged(key);
    }

    public static void removeThemed(String key, boolean dark) {
        prefs().edit().remove(themedKey(key, dark)).apply();
        notifyChanged(key);
    }

    /** Every stored setting (for exporting). */
    public static java.util.Map<String, ?> getAll() {
        return prefs().getAll();
    }

    /**
     * Replaces settings in one step (for importing): removes {@code toRemove}, then writes {@code values}
     * (Boolean, Integer, Long, Float or String). Listeners get a single "everything changed" call.
     */
    public static void replace(java.util.Collection<String> toRemove, java.util.Map<String, Object> values) {
        final SharedPreferences.Editor editor = prefs().edit();
        for (String key : toRemove) {
            editor.remove(key);
        }
        for (java.util.Map.Entry<String, Object> e : values.entrySet()) {
            final Object v = e.getValue();
            if (v instanceof Boolean) {
                editor.putBoolean(e.getKey(), (Boolean) v);
            } else if (v instanceof Integer) {
                editor.putInt(e.getKey(), (Integer) v);
            } else if (v instanceof Long) {
                editor.putLong(e.getKey(), (Long) v);
            } else if (v instanceof Float) {
                editor.putFloat(e.getKey(), (Float) v);
            } else if (v instanceof String) {
                editor.putString(e.getKey(), (String) v);
            }
        }
        editor.commit();
        notifyChanged(null);
    }

    /** Restores the default OtherGram design by dropping every stored OtherGram setting. */
    public static void resetAll() {
        prefs().edit().clear().apply();
        notifyChanged(null);
    }

    public static void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static void notifyChanged(String key) {
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : listeners) {
                listener.onOtherGramConfigChanged(key);
            }
        });
    }
}
