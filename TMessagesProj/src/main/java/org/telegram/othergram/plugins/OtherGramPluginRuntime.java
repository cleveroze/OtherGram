package org.telegram.othergram.plugins;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs enabled plugins in the embedded Python. Python starts only when at least one plugin is
 * switched on, so OtherGram without plugins pays nothing. All calls into plugins are guarded:
 * a failing plugin gets an error message in the Plugins tab instead of crashing the app.
 */
public class OtherGramPluginRuntime {

    public interface Listener {
        void onPluginsChanged();
    }

    private static volatile PyObject host;
    private static final ConcurrentHashMap<String, String> errors = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener l : listeners) {
                l.onPluginsChanged();
            }
        });
    }

    static void notifySettingsChanged(String pluginId) {
        notifyChanged();
    }

    private static synchronized PyObject host() {
        if (host == null) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            host = Python.getInstance().getModule("og_plugin_host");
        }
        return host;
    }

    public static boolean isStarted() {
        return host != null;
    }

    /** Called at app start: loads every enabled plugin in the background. */
    public static void startIfNeeded() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                boolean any = false;
                for (OtherGramPlugins.Plugin p : OtherGramPlugins.list()) {
                    if (p.enabled && OtherGramPlugins.hasConsent()) {
                        any = true;
                        loadNow(p);
                    }
                }
                if (any) {
                    appEvent("START");
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, 1500);
    }

    private static void loadNow(OtherGramPlugins.Plugin p) {
        try {
            final String error = host().callAttr("load", p.id, p.file.getAbsolutePath()).toString();
            if (error.isEmpty()) {
                errors.remove(p.id);
            } else {
                errors.put(p.id, error);
                FileLog.d("OtherGram plugin " + p.id + " failed: " + error);
            }
        } catch (Throwable e) {
            errors.put(p.id, String.valueOf(e));
            FileLog.e(e);
        }
        notifyChanged();
    }

    public static void load(OtherGramPlugins.Plugin p) {
        Utilities.globalQueue.postRunnable(() -> loadNow(p));
    }

    public static void unload(String pluginId) {
        errors.remove(pluginId);
        if (host == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                host().callAttr("unload", pluginId);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            notifyChanged();
        });
    }

    public static boolean isLoaded(String pluginId) {
        if (host == null) {
            return false;
        }
        try {
            final String ids = host.callAttr("loaded_ids").toString();
            for (String id : ids.split(",")) {
                if (id.equals(pluginId)) {
                    return true;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /** Load error, or the last error a running plugin raised; null when fine. */
    public static String error(String pluginId) {
        final String e = errors.get(pluginId);
        if (e != null) {
            return e;
        }
        if (host != null) {
            try {
                final String last = host.callAttr("last_error", pluginId).toString();
                return last.isEmpty() ? null : last;
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    public static void appEvent(String name) {
        if (host == null) {
            return;
        }
        try {
            host.callAttr("app_event", name);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ------------------------------------------------------------ hooks called by Telegram code

    /** Returns true when a plugin cancelled sending. Plugins may change params.message. */
    public static boolean onSendMessage(int account, SendMessagesHelper.SendMessageParams params) {
        final PyObject h = host;
        if (h == null || params == null) {
            return false;
        }
        try {
            if (!h.callAttr("has_send_hooks").toBoolean()) {
                return false;
            }
            return h.callAttr("on_send_message", account, params).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ------------------------------------------------------------ settings and menus

    public static JSONArray settings(String pluginId) {
        if (host == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(host.callAttr("settings_json", pluginId).toString());
        } catch (Throwable e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }

    public static String settingsAction(String pluginId, int index, String valueJson) {
        if (host == null) {
            return "";
        }
        try {
            return host.callAttr("settings_action", pluginId, index, valueJson).toString();
        } catch (Throwable e) {
            return String.valueOf(e);
        }
    }

    public static JSONArray menuItems(String menuType) {
        if (host == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(host.callAttr("menu_items_json", menuType).toString());
        } catch (Throwable e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }

    public static void menuClick(String pluginId, String itemId, int account, BaseFragment fragment, long dialogId, MessageObject message) {
        if (host == null) {
            return;
        }
        try {
            final String error = host.callAttr("menu_click", pluginId, itemId, account, fragment, dialogId, message).toString();
            if (!error.isEmpty()) {
                OtherGramPluginBridge.showBulletin("error", pluginId + ": " + firstLine(error));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    static String firstLine(String error) {
        final String[] lines = error.trim().split("\n");
        return lines[lines.length - 1];
    }
}
