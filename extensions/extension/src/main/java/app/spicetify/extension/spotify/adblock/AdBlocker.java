package app.spicetify.extension.spotify.adblock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime ad-blocking helper for Spotify on Android.
 * Filters out in-app display ads, promotional popups, home and browse feed ad banners,
 * and context menu upsell items, without modifying audio playback or audio ads.
 */
@android.annotation.SuppressLint("all")
public final class AdBlocker {

    /**
     * Known protobuf field numbers for ad sections in Spotify Home feed.
     * Field 9 = IMAGE_BRAND_AD
     * Field 10 = VIDEO_BRAND_AD
     */
    private static final Set<Integer> HOME_AD_FEATURE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(9, 10))
    );

    /**
     * Known protobuf field number for ad sections in Spotify Browse feed.
     * Field 11 = BRAND_ADS
     */
    private static final Set<Integer> BROWSE_AD_SECTION_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Collections.singletonList(11))
    );

    /**
     * Substrings identifying promotional context menu upsell items.
     */
    private static final String[] CONTEXT_MENU_AD_PATTERNS = new String[] {
            "context_menu_remove_ads",
            "playlist_entity_reinventfree_adsfree_context_menu_item",
            "play-without-ads-exp",
            "isPremiumUpsell=true"
    };

    /**
     * Account attribute keys controlling display/visual ads and promotions in the UI.
     * Audio ads attributes (such as player-license) are explicitly excluded so that
     * audio streaming remains on standard free playback without server-side sync issues.
     */
    private static final Set<String> DISPLAY_AD_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "ads",
                    "ad-formats",
                    "ad-configurations",
                    "ad-rules",
                    "sponsored-content"
            ))
    );

    private AdBlocker() {}

    /**
     * Removes brand/display ad sections from the Home feed list.
     *
     * @param sections list of section protobuf instances from HomeStructure
     */
    public static void removeHomeSections(List<?> sections) {
        if (sections == null || sections.isEmpty()) {
            return;
        }
        try {
            Iterator<?> iterator = sections.iterator();
            while (iterator.hasNext()) {
                Object section = iterator.next();
                if (section == null) continue;
                int typeId = getSectionTypeId(section, "featureTypeCase_");
                if (HOME_AD_FEATURE_TYPES.contains(typeId)) {
                    iterator.remove();
                }
            }
        } catch (Throwable ignored) {
            // Fail-safe to avoid disrupting home screen rendering
        }
    }

    /**
     * Removes brand/display ad sections from the Browse feed list.
     *
     * @param sections list of section protobuf instances from BrowseStructure
     */
    public static void removeBrowseSections(List<?> sections) {
        if (sections == null || sections.isEmpty()) {
            return;
        }
        try {
            Iterator<?> iterator = sections.iterator();
            while (iterator.hasNext()) {
                Object section = iterator.next();
                if (section == null) continue;
                int typeId = getSectionTypeId(section, "sectionTypeCase_");
                if (BROWSE_AD_SECTION_TYPES.contains(typeId)) {
                    iterator.remove();
                }
            }
        } catch (Throwable ignored) {
            // Fail-safe to avoid disrupting browse screen rendering
        }
    }

    /**
     * Checks if a context menu item is a promotional ad or premium upsell.
     */
    public static boolean isFilteredContextMenuItem(Object item) {
        if (item == null) return false;
        try {
            String str = item.toString();
            for (String pattern : CONTEXT_MENU_AD_PATTERNS) {
                if (str.contains(pattern)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Filters a list of context menu items to remove promotional upsell entries.
     */
    public static List<Object> filterContextMenuItems(List<Object> originalItems) {
        if (originalItems == null) return null;
        try {
            List<Object> filtered = new ArrayList<>(originalItems.size());
            for (Object item : originalItems) {
                if (!isFilteredContextMenuItem(item)) {
                    filtered.add(item);
                }
            }
            return filtered;
        } catch (Throwable t) {
            return originalItems;
        }
    }

    /**
     * Overrides account attributes for visual/display ads in ProductState.
     * Disables in-app banners while leaving playback/audio attributes untouched.
     */
    public static void overrideAdAttributes(Map<?, ?> attributes) {
        if (attributes == null || attributes.isEmpty()) return;
        try {
            for (Map.Entry<?, ?> entry : attributes.entrySet()) {
                Object key = entry.getKey();
                if (key != null && DISPLAY_AD_KEYS.contains(key.toString())) {
                    Object attrObj = entry.getValue();
                    if (attrObj != null) {
                        setAccountAttributeValue(attrObj, Boolean.FALSE);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void setAccountAttributeValue(Object attribute, Object value) {
        try {
            Field valueField = attribute.getClass().getDeclaredField("value_");
            valueField.setAccessible(true);
            valueField.set(attribute, value);
            return;
        } catch (Throwable ignored) {}

        // Fallback: look for any declared field containing "value"
        try {
            for (Field field : attribute.getClass().getDeclaredFields()) {
                if (field.getName().toLowerCase().contains("value")) {
                    field.setAccessible(true);
                    field.set(attribute, value);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int getSectionTypeId(Object section, String preferredFieldName) {
        // Direct field access via reflection
        try {
            Field field = section.getClass().getDeclaredField(preferredFieldName);
            field.setAccessible(true);
            Object value = field.get(section);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {}

        // Getter method for case enum (e.g. getFeatureTypeCase().getNumber())
        try {
            for (Method method : section.getClass().getMethods()) {
                String methodName = method.getName();
                if (methodName.startsWith("get") && methodName.endsWith("Case") && method.getParameterTypes().length == 0) {
                    Object enumVal = method.invoke(section);
                    if (enumVal != null) {
                        try {
                            Method getNumberMethod = enumVal.getClass().getMethod("getNumber");
                            Object numberVal = getNumberMethod.invoke(enumVal);
                            if (numberVal instanceof Number) {
                                return ((Number) numberVal).intValue();
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Search for any int field containing the preferred keyword
        try {
            String keyword = preferredFieldName.replace("_", "").toLowerCase();
            for (Field field : section.getClass().getDeclaredFields()) {
                String fieldName = field.getName().replace("_", "").toLowerCase();
                if (fieldName.equals(keyword) || fieldName.contains("typecase")) {
                    field.setAccessible(true);
                    Object val = field.get(section);
                    if (val instanceof Number) {
                        return ((Number) val).intValue();
                    }
                }
            }
        } catch (Throwable ignored) {}

        return -1;
    }

    // ---- Audio ad blocking ----

    /**
     * Strings that identify audio ad / interstitial track URIs in Spotify's playback queue.
     * Spotify audio ads are delivered as special "spotify:ad:..." URIs.
     */
    private static final String[] AUDIO_AD_URI_PATTERNS = new String[] {
            "spotify:ad:",
            "spotify:local:ad:",
            "/ad/",
            "adid=",
            "ad_session_id",
            "ad-audio"
    };

    /**
     * Returns true if the given track URI or string represents an audio ad.
     * Used by bytecode hooks to detect audio ad tracks.
     */
    public static boolean isAudioAdUri(String uri) {
        if (uri == null || uri.isEmpty()) return false;
        String lower = uri.toLowerCase();
        for (String pattern : AUDIO_AD_URI_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    /**
     * Returns true if the given Object's string representation is an audio ad.
     * Overloaded version for use with arbitrary track/item objects.
     */
    public static boolean isAudioAdObject(Object track) {
        if (track == null) return false;
        try {
            // Try to get URI field directly
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
     * Filters a playback queue list to remove audio ad entries.
     * Safe to call on any List; returns original list on error.
     */
    public static List<Object> filterAudioAds(List<Object> queue) {
        if (queue == null) return null;
        try {
            List<Object> filtered = new ArrayList<>(queue.size());
            for (Object item : queue) {
                if (!isAudioAdObject(item)) {
                    filtered.add(item);
                }
            }
            return filtered;
        } catch (Throwable t) {
            return queue;
        }
    }
}
