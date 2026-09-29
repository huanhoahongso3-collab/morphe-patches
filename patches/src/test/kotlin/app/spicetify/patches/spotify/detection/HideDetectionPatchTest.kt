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
        // Ensure no version limitation (null version targets any version)
        assertEquals(1, compat?.targets?.size)
        assertTrue(compat?.targets?.single()?.version == null, "Target version should be null for universal compatibility across all Spotify versions")
    }
}
