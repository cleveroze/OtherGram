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
