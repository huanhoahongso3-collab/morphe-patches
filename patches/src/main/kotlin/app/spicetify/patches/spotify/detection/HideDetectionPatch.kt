package app.spicetify.patches.spotify.detection

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.spicetify.patches.spotify.spotifyCompatibility

private const val EXTENSION_DETECTION_CLASS = "Lapp/spicetify/extension/spotify/detection/DetectionBypass;"

@Suppress("unused")
val hideDetectionPatch = bytecodePatch(
    name = "Hide app detection",
    description = "Hides app modifications and prevents Spotify from detecting the patched client by disabling integrity verification reporting and spoofing official package signatures.",
    default = true,
) {
    compatibleWith(spotifyCompatibility)
    extendWith("extensions/spotify.mpe")

    execute {
        // 1. Disable Play Integrity / integrity verification reporting.
        IntegrityVerificationFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.addInstructions(0, "return-void")
        }

        // 2. Inject runtime package info spoofing hook at application startup (attachBaseContext / onCreate)
        val attachContextMatches = ApplicationAttachBaseContextFingerprint.matchAllOrNull().orEmpty()
        if (attachContextMatches.isNotEmpty()) {
            attachContextMatches.forEach { match ->
                match.method.addInstructions(
                    0,
                    """
                        invoke-static { p0 }, $EXTENSION_DETECTION_CLASS->install(Landroid/content/Context;)V
                    """.trimIndent()
                )
            }
        } else {
            ApplicationOnCreateFingerprint.matchAllOrNull()?.forEach { match ->
                match.method.addInstructions(
                    0,
                    """
                        invoke-static { p0 }, $EXTENSION_DETECTION_CLASS->install(Landroid/content/Context;)V
                    """.trimIndent()
                )
            }
        }

        // 3. Patch boolean signature validity checks to return true directly without opcode mutation
        SignatureValidityFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent()
            )
        }

        // 4. Silence login session validator
        LoginSessionValidatorFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.addInstructions(0, "return-void")
        }

        // 5. Short-circuit integrity token appender to prevent header injection
        IntegrityTokenAppenderFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.addInstructions(0, "return-void")
        }
    }
}
