package app.spicetify.patches.spotify.login

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AllowGoogleSignInPatchTest {

    @Test
    fun `allow google sign-in patch has correct metadata and no version limitations`() {
        assertEquals("Allow Google Sign-In", allowGoogleSignInPatch.name)
        assertTrue(allowGoogleSignInPatch.default)

        val compat = allowGoogleSignInPatch.compatibility?.single()
        assertEquals("com.spotify.music", compat?.packageName)
        assertEquals("Spotify", compat?.name)
        // Ensure no version limitation (null version targets any version)
        assertEquals(1, compat?.targets?.size)
        assertTrue(
            compat?.targets?.single()?.version == null,
            "Target version should be null for universal compatibility across all Spotify versions"
        )
    }
}
