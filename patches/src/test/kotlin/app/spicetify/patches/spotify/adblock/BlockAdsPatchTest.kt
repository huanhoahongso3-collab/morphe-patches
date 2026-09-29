package app.spicetify.patches.spotify.adblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockAdsPatchTest {

    @Test
    fun `block ads patch has correct metadata and no version limitations`() {
        assertEquals("Block ads", blockAdsPatch.name)
        assertTrue(blockAdsPatch.default)

        val compat = blockAdsPatch.compatibility?.single()
        assertEquals("com.spotify.music", compat?.packageName)
        assertEquals("Spotify", compat?.name)
        // Ensure no version limitation (null version targets any version)
        assertEquals(1, compat?.targets?.size)
        assertTrue(compat?.targets?.single()?.version == null, "Target version should be null for universal compatibility across all Spotify versions")
    }
}
