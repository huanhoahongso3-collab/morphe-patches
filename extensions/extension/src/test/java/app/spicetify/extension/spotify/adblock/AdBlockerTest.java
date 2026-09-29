package app.spicetify.extension.spotify.adblock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdBlockerTest {

    // Dummy section class simulating Protobuf Home Section
    public static class DummyHomeSection {
        public int featureTypeCase_;

        public DummyHomeSection(int featureTypeCase) {
            this.featureTypeCase_ = featureTypeCase;
        }
    }

    // Dummy section class simulating Protobuf Browse Section
    public static class DummyBrowseSection {
        public int sectionTypeCase_;

        public DummyBrowseSection(int sectionTypeCase) {
            this.sectionTypeCase_ = sectionTypeCase;
        }
    }

    // Dummy account attribute class simulating AccountAttribute
    public static class DummyAccountAttribute {
        public Object value_;

        public DummyAccountAttribute(Object value) {
            this.value_ = value;
        }
    }

    @Test
    public void testRemoveHomeSections() {
        List<DummyHomeSection> sections = new ArrayList<>();
        sections.add(new DummyHomeSection(1)); // Regular content
        sections.add(new DummyHomeSection(9)); // IMAGE_BRAND_AD
        sections.add(new DummyHomeSection(2)); // Regular content
        sections.add(new DummyHomeSection(10)); // VIDEO_BRAND_AD
        sections.add(new DummyHomeSection(3)); // Regular content

        AdBlocker.removeHomeSections(sections);

        assertEquals(3, sections.size());
        assertEquals(1, sections.get(0).featureTypeCase_);
        assertEquals(2, sections.get(1).featureTypeCase_);
        assertEquals(3, sections.get(2).featureTypeCase_);
    }

    @Test
    public void testRemoveBrowseSections() {
        List<DummyBrowseSection> sections = new ArrayList<>();
        sections.add(new DummyBrowseSection(5)); // Regular category
        sections.add(new DummyBrowseSection(11)); // BRAND_ADS
        sections.add(new DummyBrowseSection(6)); // Regular category

        AdBlocker.removeBrowseSections(sections);

        assertEquals(2, sections.size());
        assertEquals(5, sections.get(0).sectionTypeCase_);
        assertEquals(6, sections.get(1).sectionTypeCase_);
    }

    @Test
    public void testFilterContextMenuItems() {
        List<Object> items = new ArrayList<>();
        items.add("Like song");
        items.add("context_menu_remove_ads");
        items.add("Add to playlist");
        items.add("playlist_entity_reinventfree_adsfree_context_menu_item");
        items.add("Share");

        List<Object> filtered = AdBlocker.filterContextMenuItems(items);

        assertEquals(3, filtered.size());
        assertEquals("Like song", filtered.get(0));
        assertEquals("Add to playlist", filtered.get(1));
        assertEquals("Share", filtered.get(2));
    }

    @Test
    public void testOverrideAdAttributes() {
        Map<String, DummyAccountAttribute> attributes = new HashMap<>();
        attributes.put("ads", new DummyAccountAttribute(Boolean.TRUE));
        attributes.put("ad-formats", new DummyAccountAttribute("banner,video"));
        attributes.put("player-license", new DummyAccountAttribute("free")); // Audio ads license: must NOT be touched!
        attributes.put("shuffle", new DummyAccountAttribute(Boolean.TRUE));

        AdBlocker.overrideAdAttributes(attributes);

        assertEquals(Boolean.FALSE, attributes.get("ads").value_);
        assertEquals(Boolean.FALSE, attributes.get("ad-formats").value_);
        assertEquals("free", attributes.get("player-license").value_);
        assertEquals(Boolean.TRUE, attributes.get("shuffle").value_);
    }

    @Test
    public void testIsFilteredContextMenuItem() {
        assertTrue(AdBlocker.isFilteredContextMenuItem("item with context_menu_remove_ads"));
        assertTrue(AdBlocker.isFilteredContextMenuItem("play-without-ads-exp banner"));
        assertTrue(AdBlocker.isFilteredContextMenuItem("isPremiumUpsell=true"));
        assertFalse(AdBlocker.isFilteredContextMenuItem("Add to your library"));
        assertFalse(AdBlocker.isFilteredContextMenuItem(null));
    }
}
