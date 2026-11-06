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
 * Emergency update notice delivered through /v1/config, so the owner can switch it on or off from
 * the admin panel without shipping a new build.
 *
 * soft: a dialog with "Update" and "Later"; chats keep working.
 * hard: a non-cancellable dialog for real emergencies; only the news channel stays reachable.
 *
 * Test mode is local (OtherGramConfig "emergency_test"): it shows the dialog on this device only,
 * so an admin can preview it without affecting anyone else.
 */
public class OtherGramEmergency {

    public static final String KEY_TEST = "emergency_test";

    private static final long RECHECK_MS = 5 * 60 * 1000L;
    private static OtherGramEmergency current;
    private static long checkedAt;
    private static boolean showing;
    private static boolean softShownThisProcess;

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
        softShownThisProcess = false;
    }

    /** Called from the main screen; fetches the config at most once per few minutes and shows the notice. */
    public static void checkAndShow(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (current != null && System.currentTimeMillis() - checkedAt < RECHECK_MS) {
            maybeShow(fragment);
            return;
        }
        OtherGramApi.getJson("/v1/config", (status, response) -> {
            if (status == 200 && response != null) {
                current = parse(response.optJSONObject("emergency"));
                checkedAt = System.currentTimeMillis();
                maybeShow(fragment);
            }
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

    private static void maybeShow(BaseFragment fragment) {
        final OtherGramEmergency e = current;
        if (e == null || !e.loaded || showing) {
            return;
        }
        final boolean active = e.enabled || isTestMode();
        if (!active) {
            return;
        }
        // A minimum version limits the notice to older builds; test mode shows it anyway.
        if (!isTestMode() && !TextUtils.isEmpty(e.minVersion)
                && OtherGramUpdates.compare(OtherGramConfig.VERSION, e.minVersion) >= 0) {
            return;
        }
        if (!e.hard && softShownThisProcess) {
            return;
        }
        if (fragment.getParentActivity() == null) {
            return;
        }
        showing = true;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                showDialog(fragment, e);
                if (!e.hard) {
                    softShownThisProcess = true;
                }
            } finally {
                showing = false;
            }
        }, 600);
    }

    private static void showDialog(BaseFragment fragment, OtherGramEmergency e) {
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
}
