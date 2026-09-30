package app.spicetify.patches.spotify.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackUnlockPatchTest {

    @Test
    fun `playback unlock patch has correct metadata and no version limitations`() {
        assertEquals("Unlock playback", playbackUnlockPatch.name)
        assertTrue(playbackUnlockPatch.default)

        val compat = playbackUnlockPatch.compatibility?.single()
        assertEquals("com.spotify.music", compat?.packageName)
        assertEquals("Spotify", compat?.name)
        assertEquals(1, compat?.targets?.size)
        assertTrue(compat?.targets?.single()?.version == null, "Target version should be null for universal compatibility across all Spotify versions")
    }
}
