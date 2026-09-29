package app.spicetify.patches.spotify.detection

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_DETECTION_CLASS = "Lapp/spicetify/extension/spotify/detection/DetectionBypass;"
private const val EXPECTED_SIGNATURE_SHA1 = "d6a6dced4a85f24204bf9505ccc1fce114cadb32"
private const val EXPECTED_INSTALLER_NAME = "com.android.vending"

@Suppress("unused")
val hideDetectionPatch = bytecodePatch(
    name = "Hide app detection",
    description = "Hides app modifications and prevents Spotify from detecting the patched client " +
        "by disabling integrity verification reporting and spoofing official package signatures.",
    default = true,
) {
    compatibleWith(spotifyCompatibility)
    extendWith("extensions/spotify.mpe")

    execute {
        // 1. Disable Play Integrity / integrity verification reporting.
        // Early return prevents the app from querying Google Play Integrity or transmitting
        // negative verification verdicts to Spotify's servers.
        IntegrityVerificationFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.addInstructions(0, "return-void")
        }

        // 2. Spoof internal signature verification and installer package name.
        GetPackageInfoFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()

            // Spoof signature check result to official Spotify signature
            val failedStringIndex = instructions.indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.toString()
                    ?.contains("Failed to get the application signatures") == true
            }
            if (failedStringIndex >= 0) {
                val moveResultIndex = instructions.take(failedStringIndex)
                    .indexOfLast { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                if (moveResultIndex >= 0) {
                    val signatureRegister = (instructions[moveResultIndex] as OneRegisterInstruction).registerA
                    method.replaceInstruction(
                        moveResultIndex,
                        "const-string v$signatureRegister, \"$EXPECTED_SIGNATURE_SHA1\""
                    )
                }
            }

            // Spoof installer source to Google Play Store (com.android.vending)
            instructions.forEachIndexed { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                if (reference?.name == "getInstallerPackageName" || reference?.name == "getInstallingPackageName") {
                    val nextIndex = index + 1
                    val nextInstruction = instructions.getOrNull(nextIndex)
                    if (nextInstruction?.opcode == Opcode.MOVE_RESULT_OBJECT) {
                        val installerRegister = (nextInstruction as OneRegisterInstruction).registerA
                        method.addInstructions(
                            nextIndex + 1,
                            "const-string v$installerRegister, \"$EXPECTED_INSTALLER_NAME\""
                        )
                    }
                }
            }
        }

        // 3. Inject runtime package info spoofing hook at application startup.
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
            // Fallback to onCreate if attachBaseContext was not found
            ApplicationOnCreateFingerprint.matchAllOrNull()?.forEach { match ->
                match.method.addInstructions(
                    0,
                    """
                        invoke-static { p0 }, $EXTENSION_DETECTION_CLASS->install(Landroid/content/Context;)V
                    """.trimIndent()
                )
            }
        }
    }
}
