package org.telegram.othergram.network;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.othergram.badges.OtherGramBadge;
import org.telegram.othergram.badges.OtherGramBadges;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads public OtherGram data (badges and profile decoration) of other users from the backend
 * in batches of up to 100 ids, and keeps the decoration in memory for the profile screen.
 */
public class OtherGramProfiles {

    private static final int MAX_IDS_PER_REQUEST = 100;

    /** Public profile decoration published by a user. */
    public static class Profile {
        public static final String BANNER_IMAGE = "image";
        public static final String BANNER_COLOR = "color";

        public String bannerKind;
        public String bannerUrl;
        public float bannerOffset = 0.5f;
        public int bannerColor;
        public Integer nameColor;
        public Integer bioColor;
        public Integer accentColor;
        /** Avatar ring: "solid", "gradient", "dashed", "double" or null. */
        public String ringStyle;
        public int ringColor;
        public int ringColor2;
        /** Second name color: when set, the name is drawn as a gradient from nameColor to nameColor2. */
        public Integer nameColor2;
        /** Bubble style: "solid", "glass" or null; color and opacity in percent. */
        public String bubbleStyle;
        public int bubbleColor;
        public int bubbleAlpha = 100;
    }

    private static final Map<Long, Profile> profiles = new ConcurrentHashMap<>();

    /** Registers the badge fetcher once the backend address is configured. */
    public static void init() {
        if (OtherGramApi.isConfigured()) {
            OtherGramBadges.setFetcher(OtherGramProfiles::fetch);
        }
    }

    /** Decoration of the user, or null when the user published none or it is not loaded yet. */
    public static Profile getProfile(long userId) {
        return profiles.get(userId);
    }

    /** The name painted with the color (or two-color gradient) the user published, or the name unchanged. */
    public static CharSequence colorName(CharSequence name, long userId) {
        final Profile profile = userId != 0 && name != null && name.length() > 0 ? profiles.get(userId) : null;
        if (profile == null || profile.nameColor == null) {
            return name;
        }
        final android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(name);
        final Object span = profile.nameColor2 != null
                ? new GradientNameSpan(name.toString(), profile.nameColor | 0xFF000000, profile.nameColor2 | 0xFF000000)
                : new android.text.style.ForegroundColorSpan(profile.nameColor | 0xFF000000);
        sb.setSpan(span, 0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** Paints the covered text with a horizontal gradient across its own width. */
    public static class GradientNameSpan extends android.text.style.CharacterStyle implements android.text.style.UpdateAppearance {
        private final String text;
        private final int from;
        private final int to;

        public GradientNameSpan(String text, int from, int to) {
            this.text = text;
            this.from = from;
            this.to = to;
        }

        @Override
        public void updateDrawState(android.text.TextPaint tp) {
            final float width = Math.max(1f, tp.measureText(text));
            tp.setShader(new android.graphics.LinearGradient(0, 0, width, 0, from, to, android.graphics.Shader.TileMode.CLAMP));
        }
    }

    public static void fetch(Set<Long> userIds) {
        final List<Long> ids = new ArrayList<>(userIds);
        for (int from = 0; from < ids.size(); from += MAX_IDS_PER_REQUEST) {
            final List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + MAX_IDS_PER_REQUEST));
            final java.util.Set<Long> chunkIds = new java.util.HashSet<>(chunk);
            OtherGramApi.getJson("/v1/profiles?ids=" + TextUtils.join(",", chunk), (status, response) -> {
                if (status == 200 && response != null) {
                    apply(response.optJSONObject("users"));
                } else {
                    OtherGramBadges.onFetchFailed(chunkIds); // retry later instead of never showing them
                }
            });
        }
    }

    public interface MeCallback {
        void onDone(int status, JSONObject me);
    }

    /**
     * Changes some fields of the own published profile. The server replaces the profile on PUT, so the
     * current profile is read first and only {@code changes} are applied on top of it.
     */
    public static void updateMyProfile(JSONObject changes, MeCallback callback) {
        OtherGramApi.getJson("/v1/me", (status, me) -> {
            if (status != 200 || me == null) {
                callback.onDone(status, me);
                return;
            }
            final JSONObject body = new JSONObject();
            try {
                final JSONObject p = me.optJSONObject("profile");
                final JSONObject banner = p != null ? p.optJSONObject("banner") : null;
                body.put("banner_kind", banner != null ? banner.optString("kind") : JSONObject.NULL);
                if (banner != null && banner.has("color")) {
                    body.put("banner_color", banner.optInt("color"));
                }
                if (banner != null && banner.has("offset")) {
                    body.put("banner_offset", banner.optDouble("offset", 0.5));
                }
                for (String key : new String[]{"name_color", "bio_color", "accent_color", "name_color2"}) {
                    body.put(key, p != null && !p.isNull(key) ? p.opt(key) : JSONObject.NULL);
                }
                final JSONObject ring = p != null ? p.optJSONObject("ring") : null;
                body.put("ring_style", ring != null ? ring.optString("style") : JSONObject.NULL);
                if (ring != null) {
                    body.put("ring_color", ring.optInt("color"));
                    body.put("ring_color2", ring.optInt("color2"));
                }
                final Iterator<String> keys = changes.keys();
                while (keys.hasNext()) {
                    final String key = keys.next();
                    body.put(key, changes.get(key));
                }
            } catch (Exception e) {
                callback.onDone(0, null);
                return;
            }
            OtherGramApi.sendJson("PUT", "/v1/me/profile", body, (s, response) -> {
                if (s == 200) {
                    applyMe(response);
                }
                callback.onDone(s, response);
            });
        });
    }

    /** Applies a /v1/me response (own badges and profile) to the caches so own screens update at once. */
    public static void applyMe(JSONObject me) {
        if (me == null || !me.has("user_id")) {
            return;
        }
        try {
            final JSONObject users = new JSONObject();
            users.put(String.valueOf(me.optLong("user_id")), me);
            apply(users);
        } catch (Exception ignore) {
        }
    }

    static void apply(JSONObject users) {
        if (users == null) {
            return;
        }
        final Map<Long, List<OtherGramBadge>> badges = new HashMap<>();
        final Iterator<String> keys = users.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final long userId;
            try {
                userId = Long.parseLong(key);
            } catch (NumberFormatException e) {
                continue;
            }
            final JSONObject user = users.optJSONObject(key);
            if (user == null) {
                continue;
            }
            badges.put(userId, parseBadges(user.optJSONArray("badges")));
            final Profile profile = parseProfile(user.optJSONObject("profile"));
            if (profile != null) {
                profiles.put(userId, profile);
            } else {
                profiles.remove(userId);
            }
        }
        OtherGramBadges.put(badges);
    }

    static List<OtherGramBadge> parseBadges(JSONArray array) {
        final List<OtherGramBadge> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            final JSONObject b = array.optJSONObject(i);
            if (b != null) {
                list.add(new OtherGramBadge(b.optString("id"), b.optString("title"), b.optString("label"), b.optString("description"), b.optInt("color"), b.optString("icon", OtherGramBadge.ICON_CHECK)));
            }
        }
        return list;
    }

    static Profile parseProfile(JSONObject json) {
        if (json == null) {
            return null;
        }
        final Profile p = new Profile();
        final JSONObject banner = json.optJSONObject("banner");
        if (banner != null) {
            p.bannerKind = banner.optString("kind", null);
            if (Profile.BANNER_IMAGE.equals(p.bannerKind)) {
                p.bannerUrl = OtherGramApi.BASE_URL + banner.optString("url");
                p.bannerOffset = (float) banner.optDouble("offset", 0.5);
            } else if (Profile.BANNER_COLOR.equals(p.bannerKind)) {
                p.bannerColor = banner.optInt("color");
            }
        }
        p.nameColor = json.isNull("name_color") ? null : json.optInt("name_color");
        p.bioColor = json.isNull("bio_color") ? null : json.optInt("bio_color");
        p.accentColor = json.isNull("accent_color") ? null : json.optInt("accent_color");
        p.nameColor2 = json.isNull("name_color2") ? null : json.optInt("name_color2");
        final JSONObject bubble = json.optJSONObject("bubble");
        if (bubble != null) {
            p.bubbleStyle = bubble.optString("style", null);
            p.bubbleColor = bubble.optInt("color", 0xFFA68770);
            p.bubbleAlpha = bubble.optInt("alpha", 100);
        }
        final JSONObject ring = json.optJSONObject("ring");
        if (ring != null) {
            p.ringStyle = ring.optString("style", null);
            p.ringColor = ring.optInt("color", 0xFFA68770);
            p.ringColor2 = ring.optInt("color2", p.ringColor);
        }
        return p;
    }
}
