package org.telegram.othergram;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Notices shown over the main screen: the "new version on GitHub" plaque with the OtherGram banner,
 * and the server emergency notice from /v1/config.
 *
 * The update plaque appears on every entry into the app (with a short cooldown so it does not pop
 * twice during one resume burst) and keeps returning until the user actually installs a newer build.
 * The server notice wins when it is enabled; test mode shows it on this device only.
 */
public class OtherGramEmergency {

    public static final String KEY_TEST = "emergency_test";

    private static final long RECHECK_MS = 5 * 60 * 1000L;
    /** Do not show the plaque again within this window; a fresh cold start resets it. */
    private static final long SHOW_COOLDOWN_MS = 60 * 1000L;

    private static OtherGramEmergency current;
    private static long checkedAt;
    private static long lastShownAt;
    private static boolean showing;

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
        lastShownAt = 0;
    }

    /** Called from the main screen: fetches the config, then shows the server notice or the update plaque. */
    public static void checkAndShow(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (System.currentTimeMillis() - lastShownAt < SHOW_COOLDOWN_MS) {
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
        if (e == null || !e.loaded || showing) {
            return;
        }
        if (fragment.getParentActivity() == null) {
            return;
        }
        lastShownAt = System.currentTimeMillis();
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
        if (showing) {
            return;
        }
        OtherGramUpdates.check((version, url) -> {
            if (version == null || !OtherGramUpdates.isNewer(version) || showing) {
                return;
            }
            lastShownAt = System.currentTimeMillis();
            showing = true;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    showUpdateDialog(fragment, url);
                } finally {
                    showing = false;
                }
            }, 600);
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

    private static void showUpdateDialog(BaseFragment fragment, String url) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final String target = url != null ? url : "https://github.com/cleveroze/OtherGram/releases/latest";
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(buildUpdateView(activity))
                .setPositiveButton(LocaleController.getString(R.string.OG_EmergencyUpdate), (d, w) ->
                        org.telegram.messenger.browser.Browser.openUrl(activity, target))
                .setNegativeButton(LocaleController.getString(R.string.OG_EmergencyLater), null)
                .create();
        fragment.showDialog(dialog);
    }

    /** The banner the owner supplied, with the localized "Update available!" line over its lower part. */
    private static View buildUpdateView(Activity activity) {
        final FrameLayout root = new FrameLayout(activity);
        final ImageView banner = new ImageView(activity);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setImageResource(R.drawable.og_update_banner);
        root.addView(banner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 172, Gravity.TOP));

        final TextView label = new TextView(activity);
        label.setText(LocaleController.getString(R.string.OG_UpdatePlaqueText));
        label.setTextColor(0xFF30241C);
        label.setTextSize(17);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(AndroidUtilities.bold());
        label.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        label.setBackgroundColor(0xB3FFF7ED);
        root.addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 10));
        return root;
    }
}
