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
     */
    private static final String[] BOOLEAN_ENABLE_KEYS = {
            "shuffle",
            "on-demand",
            "player-level-timeout",
            "radio-ad-supported"        // set to false to suppress radio ads
    };

    private static final boolean[] BOOLEAN_ENABLE_VALUES = {
            true,   // shuffle: always enabled
            true,   // on-demand: allow selecting any track
            false,  // player-level-timeout: disable session timeout
            false   // radio-ad-supported: disable radio audio ads
    };

    /**
     * Integer capability flags for skip counts.
     */
    private static final String[] INTEGER_KEYS = {
            "skip",              // number of skips per hour; max int = unlimited
            "skip-after-ad"      // skips allowed after an audio ad
    };

    private static final int[] INTEGER_VALUES = {
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
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
     * Selectively overrides playback feature flags (shuffle, on-demand, skip limits, ad suppression)
     * while leaving account identity ("player-license", "player-type") intact to prevent detection.
     *
     * @param attributes The ProductState attribute map (key → attribute object)
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

    /**
     * Returns true if the given URI belongs to an audio ad or interstitial.
     */
    public static boolean isAudioAdUri(String uri) {
        if (uri == null || uri.isEmpty()) return false;
        String lower = uri.toLowerCase();
        for (String pattern : AUDIO_AD_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    /**
     * Returns true if the given track object represents an audio ad.
     */
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

    /**
     * Direct query used by playback hook to determine if an audio ad track should be skipped immediately.
     */
    public static boolean shouldSkipAudioAd(Object track) {
        return isAudioAd(track);
    }

    /**
     * Filters a list of queue items, stripping out all audio ad tracks.
     */
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

    /**
     * Returns 0 for remaining ad duration so the player treats any audio ad as finished.
     */
    public static int getAdDurationRemaining() {
        return 0;
    }

    // ---- Playback Feature Override Functions ----

    /**
     * Returns true for next/skip allowed checks.
     * Injected into methods that check if next/skip button should be enabled.
     */
    public static boolean isNextAllowed() {
        return true;
    }

    /**
     * Returns the maximum int value for skip count remaining.
     * Injected into methods that calculate hourly skip limits.
     */
    public static int getSkipsRemaining() {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns true for shuffle allowed check.
     * Injected into methods that gate shuffle button and mode.
     */
    public static boolean isShuffleAllowed() {
        return true;
    }

    /**
     * Returns true for on-demand track selection allowed check.
     * Injected into methods that gate manual track selection.
     */
    public static boolean isOnDemandAllowed() {
        return true;
    }

    // ---- Reflection Helper ----

    private static void setAttributeValue(Object attribute, Object value) {
        try {
            Field f = attribute.getClass().getDeclaredField("value_");
            f.setAccessible(true);
            f.set(attribute, value);
            return;
        } catch (Throwable ignored) {}

        try {
            for (Field f : attribute.getClass().getDeclaredFields()) {
                if (fieldHasValueName(f)) {
                    f.setAccessible(true);
                    try {
                        f.set(attribute, value);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Method m : attribute.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("setvalue") &&
                        m.getParameterTypes().length == 1) {
                    try {
                        m.invoke(attribute, value);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean fieldHasValueName(Field f) {
        return f.getName().toLowerCase().contains("value");
    }
}
