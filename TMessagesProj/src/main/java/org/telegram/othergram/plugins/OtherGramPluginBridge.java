package org.telegram.othergram.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Static helpers the Python plugin API calls (settings storage, bulletins, threads, sending text).
 * Kept small and explicit: plugins reach the app through these methods and through Java classes.
 */
public class OtherGramPluginBridge {

    private static SharedPreferences settings(String pluginId) {
        return ApplicationLoader.applicationContext.getSharedPreferences("othergram_plugin_" + pluginId, Context.MODE_PRIVATE);
    }

    public static String getSetting(String pluginId, String key) {
        return settings(pluginId).getString(key, null);
    }

    public static void setSetting(String pluginId, String key, String json) {
        settings(pluginId).edit().putString(key, json).apply();
    }

    public static String exportSettings(String pluginId) {
        final JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> e : settings(pluginId).getAll().entrySet()) {
            try {
                out.put(e.getKey(), new org.json.JSONTokener(String.valueOf(e.getValue())).nextValue());
            } catch (Exception ignore) {
            }
        }
        return out.toString();
    }

    public static void clearSettings(String pluginId) {
        settings(pluginId).edit().clear().apply();
    }

    public static void reloadSettings(String pluginId) {
        AndroidUtilities.runOnUIThread(() -> OtherGramPluginRuntime.notifySettingsChanged(pluginId));
    }

    public static void log(String tag, String message) {
        FileLog.d("OtherGram plugin " + tag + ": " + message);
    }

    public static void runOnUiThread(Runnable runnable, int delay) {
        AndroidUtilities.runOnUIThread(runnable, Math.max(0, delay));
    }

    public static void runOnBackground(Runnable runnable, int delay) {
        Utilities.globalQueue.postRunnable(runnable, Math.max(0, delay));
    }

    public static void copyToClipboard(String text) {
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.addToClipboard(text));
    }

    public static void sendText(int account, long peer, String text) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer)));
    }

    public static void showBulletin(String kind, String text) {
        AndroidUtilities.runOnUIThread(() -> {
            final BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment == null) {
                return;
            }
            if ("error".equals(kind)) {
                BulletinFactory.of(fragment).createErrorBulletin(text).show();
            } else {
                BulletinFactory.of(fragment).createSimpleBulletin("success".equals(kind) ? R.raw.done : R.raw.info, text).show();
            }
        });
    }

    public static void showAlert(String title, String message, String positive, Runnable onPositive, String negative, Runnable onNegative) {
        AndroidUtilities.runOnUIThread(() -> {
            final BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment == null || fragment.getParentActivity() == null) {
                return;
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            if (title != null) builder.setTitle(title);
            if (message != null) builder.setMessage(message);
            builder.setPositiveButton(positive != null ? positive : "OK", (d, w) -> {
                if (onPositive != null) onPositive.run();
            });
            if (negative != null) {
                builder.setNegativeButton(negative, (d, w) -> {
                    if (onNegative != null) onNegative.run();
                });
            }
            fragment.showDialog(builder.create());
        });
    }

    // ------------------------------------------------------------ reflection helpers for hook_utils

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static Object getField(Object obj, String name) {
        try {
            return findField(obj.getClass(), name).get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setField(Object obj, String name, Object value) {
        try {
            findField(obj.getClass(), name).set(obj, value);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static Object getStaticField(Class<?> cls, String name) {
        try {
            return findField(cls, name).get(null);
        } catch (Exception e) {
            return null;
        }
    }
}
