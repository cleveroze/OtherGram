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

    public static void fetch(Set<Long> userIds) {
        final List<Long> ids = new ArrayList<>(userIds);
        for (int from = 0; from < ids.size(); from += MAX_IDS_PER_REQUEST) {
            final List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + MAX_IDS_PER_REQUEST));
            OtherGramApi.getJson("/v1/profiles?ids=" + TextUtils.join(",", chunk), (status, response) -> {
                if (status == 200 && response != null) {
                    apply(response.optJSONObject("users"));
                }
            });
        }
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
                list.add(new OtherGramBadge(b.optString("id"), b.optString("title"), b.optString("description"), b.optInt("color"), b.optString("icon", OtherGramBadge.ICON_CHECK)));
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
        return p;
    }
}
