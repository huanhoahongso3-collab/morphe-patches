package app.spicetify.extension.spotify.playback;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stealth runtime helper for audio ad blocking and playback restrictions bypass.
 *
 * To minimize server-side detection, this class DOES NOT modify account identity
 * keys such as "player-license" or "player-type" to "premium".
 * Instead, it selectively unlocks feature capability flags (shuffle, on-demand,
 * skip limits) and provides runtime checks to detect and skip audio ad tracks.
 */
@android.annotation.SuppressLint("all")
public final class PlaybackUnlocker {

    /**
     * Account capability flags to enable directly for playback without spoofing premium identity.
     * Values are strings matching Spotify's Protobuf AccountAttributeProto schema.
     */
    private static final String[] BOOLEAN_ENABLE_KEYS = {
            "shuffle",
            "on-demand",
            "player-level-timeout",
            "radio-ad-supported"
    };

    private static final String[] BOOLEAN_ENABLE_VALUES = {
            "1",    // shuffle: enabled ("1")
            "1",    // on-demand: enabled ("1")
            "0",    // player-level-timeout: disabled ("0")
            "0"     // radio-ad-supported: disabled ("0")
    };

    /**
     * Integer capability flags for skip counts.
     */
    private static final String[] INTEGER_KEYS = {
            "skip",
            "skip-after-ad"
    };

    private static final String[] INTEGER_VALUES = {
            "2147483647",
            "2147483647"
    };

    /**
     * Keys to disable audio/display ad injection in ProductState without touching account license.
     */
    private static final String[] DISABLE_AD_KEYS = {
            "audio-ads",
            "ad-formats",
            "ads",
            "sponsored-content"
    };

    /**
     * Key patterns used to detect audio ad track URIs.
     */
    private static final String[] AUDIO_AD_PATTERNS = {
            "spotify:ad:",
            "spotify:local:ad:",
            "/ad/",
            "adid=",
            "ad_session_id",
            "ad-audio",
            "interstitial"
    };

    /**
     * Keys that identify account identity/tier.
     * We explicitly DO NOT modify these to avoid detection by server telemetry.
     */
    private static final Set<String> SENSITIVE_IDENTITY_KEYS = new HashSet<>(Arrays.asList(
            "player-license",
            "player-type",
            "type"
    ));

    private PlaybackUnlocker() {}

    /**
     * Stealth unlocker for ProductState attributes.
     */
    public static void unlockPlaybackAttributes(Map<?, ?> attributes) {
        unlockPlaybackWithoutPremiumToggle(attributes);
    }

    /**
     * Selectively unlocks playback capabilities without toggling the global premium account tier.
     * This reduces detection risk by preserving "player-license": "free" server state alignment.
     *
     * @param attributes The ProductState attribute map
     */
    public static void unlockPlaybackWithoutPremiumToggle(Map<?, ?> attributes) {
        if (attributes == null || attributes.isEmpty()) return;
        try {
            for (Map.Entry<?, ?> entry : attributes.entrySet()) {
                Object key = entry.getKey();
                if (key == null) continue;
                String keyStr = key.toString();

                // Skip sensitive account identity keys to avoid server discrepancy detection
                if (SENSITIVE_IDENTITY_KEYS.contains(keyStr)) {
                    continue;
                }

                Object attr = entry.getValue();
                if (attr == null) continue;

                // Enable boolean capability flags
                for (int i = 0; i < BOOLEAN_ENABLE_KEYS.length; i++) {
                    if (keyStr.equals(BOOLEAN_ENABLE_KEYS[i])) {
                        setAttributeValue(attr, BOOLEAN_ENABLE_VALUES[i]);
                    }
                }

                // Override skip limits to max int
                for (int i = 0; i < INTEGER_KEYS.length; i++) {
                    if (keyStr.equals(INTEGER_KEYS[i])) {
                        setAttributeValue(attr, INTEGER_VALUES[i]);
                    }
                }

                // Disable ad-related flags
                for (String disableKey : DISABLE_AD_KEYS) {
                    if (keyStr.equals(disableKey)) {
                        setAttributeValue(attr, "0");
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    // ---- Audio Ad Blocking & Skip Logic ----

    public static boolean isAudioAdUri(String uri) {
        if (uri == null || uri.isEmpty()) return false;
        String lower = uri.toLowerCase();
        for (String pattern : AUDIO_AD_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    public static boolean isAudioAd(Object track) {
        if (track == null) return false;
        try {
            for (Field f : track.getClass().getDeclaredFields()) {
                if (f.getName().toLowerCase().contains("uri") ||
                        f.getName().toLowerCase().contains("id")) {
                    f.setAccessible(true);
                    Object val = f.get(track);
                    if (val instanceof String && isAudioAdUri((String) val)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return isAudioAdUri(track.toString());
    }

    public static boolean shouldSkipAudioAd(Object track) {
        return isAudioAd(track);
    }

    public static List<Object> filterAudioAds(List<Object> queue) {
        if (queue == null) return null;
        try {
            List<Object> filtered = new ArrayList<>(queue.size());
            for (Object item : queue) {
                if (!isAudioAd(item)) {
                    filtered.add(item);
                }
            }
            return filtered;
        } catch (Throwable t) {
            return queue;
        }
    }

    public static int getAdDurationRemaining() {
        return 0;
    }

    // ---- Playback Feature Override Functions ----

    public static boolean isNextAllowed() {
        return true;
    }

    public static int getSkipsRemaining() {
        return Integer.MAX_VALUE;
    }

    public static boolean isShuffleAllowed() {
        return true;
    }

    public static boolean isOnDemandAllowed() {
        return true;
    }

    // ---- Reflection Helper ----

    private static void setAttributeValue(Object attribute, Object value) {
        if (attribute == null || value == null) return;
        try {
            Field f = null;
            try {
                f = attribute.getClass().getDeclaredField("value_");
            } catch (Throwable ignored) {
                for (Field declared : attribute.getClass().getDeclaredFields()) {
                    if (declared.getName().toLowerCase().contains("value")) {
                        f = declared;
                        break;
                    }
                }
            }

            if (f == null) return;
            f.setAccessible(true);
            Class<?> targetType = f.getType();

            if (targetType == String.class) {
                if (value instanceof Boolean) {
                    f.set(attribute, ((Boolean) value) ? "1" : "0");
                } else {
                    f.set(attribute, String.valueOf(value));
                }
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                if (value instanceof Boolean) {
                    f.set(attribute, value);
                } else if (value instanceof Number) {
                    f.set(attribute, ((Number) value).intValue() != 0);
                } else {
                    String s = String.valueOf(value);
                    f.set(attribute, "1".equals(s) || "true".equalsIgnoreCase(s));
                }
            } else if (targetType == int.class || targetType == Integer.class) {
                if (value instanceof Number) {
                    f.set(attribute, ((Number) value).intValue());
                } else {
                    try {
                        f.set(attribute, Integer.parseInt(String.valueOf(value)));
                    } catch (Throwable ignored) {
                        f.set(attribute, 0);
                    }
                }
            } else {
                f.set(attribute, value);
            }
        } catch (Throwable ignored) {}
    }
}
