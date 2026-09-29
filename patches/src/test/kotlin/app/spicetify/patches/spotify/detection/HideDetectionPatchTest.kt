package app.spicetify.patches.spotify.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HideDetectionPatchTest {

    @Test
    fun `hide detection patch has correct metadata and no version limitations`() {
        assertEquals("Hide app detection", hideDetectionPatch.name)
        assertTrue(hideDetectionPatch.default)

        val compat = hideDetectionPatch.compatibility?.single()
        assertEquals("com.spotify.music", compat?.packageName)
        assertEquals("Spotify", compat?.name)
        // Ensure no version limitation
        assertTrue(compat?.targets?.isEmpty() == true, "Targets should be empty for universal compatibility across all Spotify versions")
    }
}
