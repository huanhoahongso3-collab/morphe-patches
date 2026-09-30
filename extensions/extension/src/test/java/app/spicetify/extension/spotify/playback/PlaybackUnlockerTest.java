package app.spicetify.extension.spotify.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackUnlockerTest {

    public static class DummyAccountAttribute {
        public Object value_;

        public DummyAccountAttribute(Object value) {
            this.value_ = value;
        }
    }

    public static class DummyTrack {
        public String uri;

        public DummyTrack(String uri) {
            this.uri = uri;
        }

        @Override
        public String toString() {
            return uri;
        }
    }

    @Test
    public void testUnlockPlaybackWithoutPremiumTogglePreservesIdentity() {
        Map<String, DummyAccountAttribute> attributes = new HashMap<>();
        attributes.put("player-license", new DummyAccountAttribute("free"));
        attributes.put("player-type", new DummyAccountAttribute("free"));
        attributes.put("type", new DummyAccountAttribute("free"));
        attributes.put("shuffle", new DummyAccountAttribute(Boolean.FALSE));
        attributes.put("on-demand", new DummyAccountAttribute(Boolean.FALSE));
        attributes.put("skip", new DummyAccountAttribute(6));
        attributes.put("audio-ads", new DummyAccountAttribute("1"));

        PlaybackUnlocker.unlockPlaybackWithoutPremiumToggle(attributes);

        // Account identity keys MUST stay "free" to avoid detection by server telemetry
        assertEquals("free", attributes.get("player-license").value_);
        assertEquals("free", attributes.get("player-type").value_);
        assertEquals("free", attributes.get("type").value_);

        // Feature flags are unlocked
        assertEquals(Boolean.TRUE, attributes.get("shuffle").value_);
        assertEquals(Boolean.TRUE, attributes.get("on-demand").value_);
        assertEquals(Integer.MAX_VALUE, attributes.get("skip").value_);
        assertEquals("0", attributes.get("audio-ads").value_);
    }

    @Test
    public void testIsAudioAdUri() {
        assertTrue(PlaybackUnlocker.isAudioAdUri("spotify:ad:123456"));
        assertTrue(PlaybackUnlocker.isAudioAdUri("spotify:local:ad:promo"));
        assertTrue(PlaybackUnlocker.isAudioAdUri("https://spclient.spotify.com/ad/stream"));
        assertTrue(PlaybackUnlocker.isAudioAdUri("spotify:track:123?ad_session_id=789"));
        assertFalse(PlaybackUnlocker.isAudioAdUri("spotify:track:4uLU6AcVwvNuMAtmGStEfM"));
        assertFalse(PlaybackUnlocker.isAudioAdUri(null));
    }

    @Test
    public void testIsAudioAdObjectAndFilter() {
        DummyTrack track1 = new DummyTrack("spotify:track:normal1");
        DummyTrack adTrack = new DummyTrack("spotify:ad:commercial");
        DummyTrack track2 = new DummyTrack("spotify:track:normal2");

        assertTrue(PlaybackUnlocker.isAudioAd(adTrack));
        assertFalse(PlaybackUnlocker.isAudioAd(track1));
        assertTrue(PlaybackUnlocker.shouldSkipAudioAd(adTrack));

        List<Object> queue = new ArrayList<>();
        queue.add(track1);
        queue.add(adTrack);
        queue.add(track2);

        List<Object> filtered = PlaybackUnlocker.filterAudioAds(queue);
        assertEquals(2, filtered.size());
        assertEquals(track1, filtered.get(0));
        assertEquals(track2, filtered.get(1));
    }

    @Test
    public void testPlaybackOverrides() {
        assertTrue(PlaybackUnlocker.isNextAllowed());
        assertTrue(PlaybackUnlocker.isShuffleAllowed());
        assertTrue(PlaybackUnlocker.isOnDemandAllowed());
        assertEquals(Integer.MAX_VALUE, PlaybackUnlocker.getSkipsRemaining());
        assertEquals(0, PlaybackUnlocker.getAdDurationRemaining());
    }
}
