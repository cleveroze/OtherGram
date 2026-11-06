package org.telegram.othergram;

import android.app.Activity;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Launcher notices: an emergency update from the server (/v1/config) and a "new version on GitHub"
 * plaque. Both are shown over the main screen on every cold start.
 *
 * The server notice wins when it is enabled; otherwise the GitHub notice appears when the latest
 * release is newer than the installed build. Neither is shown again until the app is restarted,
 * and the GitHub notice keeps returning on every launch until the user actually updates.
 *
 * Test mode (OtherGramConfig "emergency_test") shows the server notice on this device only, so an
 * admin can preview it without affecting anyone.
 */
public class OtherGramEmergency {

    public static final String KEY_TEST = "emergency_test";

    private static final long RECHECK_MS = 5 * 60 * 1000L;
    private static OtherGramEmergency current;
    private static long checkedAt;
    private static boolean showing;
    private static boolean noticeShownThisProcess;

    public boolean loaded;
    public boolean enabled;
    public boolean hard;
    public String title;
    public String text;
    public String link;
    public String minVersion;
    public String releasesUrl;
    public String newsUrl;

    public static boolean isTestMode() {
        return OtherGramConfig.getBoolean(KEY_TEST, false);
    }

    public static void setTestMode(boolean on) {
        OtherGramConfig.putBoolean(KEY_TEST, on);
        noticeShownThisProcess = false;
    }

    /** Called from the main screen: fetches the config, then shows the server notice or the update plaque. */
    public static void checkAndShow(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (current != null && System.currentTimeMillis() - checkedAt < RECHECK_MS) {
            dispatch(fragment);
            return;
        }
        OtherGramApi.getJson("/v1/config", (status, response) -> {
            current = parse(status == 200 && response != null ? response.optJSONObject("emergency") : null);
            checkedAt = System.currentTimeMillis();
            dispatch(fragment);
        });
    }

    private static OtherGramEmergency parse(JSONObject e) {
        final OtherGramEmergency out = new OtherGramEmergency();
        out.loaded = true;
        out.newsUrl = "https://t.me/OtherByGram";
        out.releasesUrl = "https://t.me/OtherReleases";
        if (e == null) {
            return out;
        }
        out.enabled = e.optBoolean("enabled", false);
        out.hard = "hard".equals(e.optString("mode", "soft"));
        out.title = e.optString("title", "");
        out.text = e.optString("text", "");
        out.link = e.optString("link", "");
        out.minVersion = e.optString("min_version", "");
        final JSONObject channels = e.optJSONObject("channels");
        if (channels != null) {
            out.releasesUrl = channels.optString("releases", out.releasesUrl);
            out.newsUrl = channels.optString("news", out.newsUrl);
        }
        return out;
    }

    private static void dispatch(BaseFragment fragment) {
        if (current != null && isEmergencyActive(current)) {
            maybeShowEmergency(fragment);
        } else {
            checkUpdate(fragment);
        }
    }

    private static boolean isEmergencyActive(OtherGramEmergency e) {
        if (!e.enabled && !isTestMode()) {
            return false;
        }
        // A minimum version limits the notice to older builds; test mode shows it anyway.
        if (!isTestMode() && !TextUtils.isEmpty(e.minVersion)
                && OtherGramUpdates.compare(OtherGramConfig.VERSION, e.minVersion) >= 0) {
            return false;
        }
        return true;
    }

    private static void maybeShowEmergency(BaseFragment fragment) {
        final OtherGramEmergency e = current;
        if (e == null || !e.loaded || showing || noticeShownThisProcess) {
            return;
        }
        if (fragment.getParentActivity() == null) {
            return;
        }
        noticeShownThisProcess = true;
        showing = true;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                showEmergencyDialog(fragment, e);
            } finally {
                showing = false;
            }
        }, 600);
    }

    private static void checkUpdate(BaseFragment fragment) {
        if (noticeShownThisProcess) {
            return;
        }
        OtherGramUpdates.check((version, url) -> {
            if (version == null || !OtherGramUpdates.isNewer(version) || noticeShownThisProcess) {
                return;
            }
            noticeShownThisProcess = true;
            AndroidUtilities.runOnUIThread(() -> showUpdateDialog(fragment, version, url), 600);
        });
    }

    private static void showEmergencyDialog(BaseFragment fragment, OtherGramEmergency e) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final String title = TextUtils.isEmpty(e.title) ? LocaleController.getString(R.string.OG_EmergencyTitle) : e.title;
        final String text = TextUtils.isEmpty(e.text) ? LocaleController.getString(R.string.OG_EmergencyText) : e.text;
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(text);
        if (e.hard) {
            builder.setPositiveButton(LocaleController.getString(R.string.OG_EmergencyOpenChannel), (d, w) ->
                    org.telegram.messenger.browser.Browser.openUrl(activity, e.newsUrl));
            final AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            fragment.showDialog(dialog);
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.OG_EmergencyUpdate), (d, w) ->
                    org.telegram.messenger.browser.Browser.openUrl(activity, TextUtils.isEmpty(e.link) ? e.releasesUrl : e.link));
            builder.setNegativeButton(LocaleController.getString(R.string.OG_EmergencyLater), null);
            fragment.showDialog(builder.create());
        }
    }

    private static void showUpdateDialog(BaseFragment fragment, String version, String url) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final String target = url != null ? url : "https://github.com/cleveroze/OtherGram/releases/latest";
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.OG_NewVersionTitle))
                .setMessage(LocaleController.formatString(R.string.OG_NewVersionText, version))
                .setPositiveButton(LocaleController.getString(R.string.OG_EmergencyUpdate), (d, w) ->
                        org.telegram.messenger.browser.Browser.openUrl(activity, target))
                .setNegativeButton(LocaleController.getString(R.string.OG_EmergencyLater), null)
                .create();
        fragment.showDialog(dialog);
    }
}
