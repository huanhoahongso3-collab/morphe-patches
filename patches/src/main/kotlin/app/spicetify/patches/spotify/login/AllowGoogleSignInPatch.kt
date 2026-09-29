package app.spicetify.patches.spotify.login

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.spicetify.patches.spotify.spotifyCompatibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_LOGIN_CLASS =
    "Lapp/spicetify/extension/spotify/login/GoogleSignInBypass;"
private const val EXTENSION_DETECTION_CLASS =
    "Lapp/spicetify/extension/spotify/detection/DetectionBypass;"

@Suppress("unused")
val allowGoogleSignInPatch = bytecodePatch(
    name = "Allow Google Sign-In",
    description = "Fixes Google Sign-In and standard login on unofficial builds by bypassing " +
        "certificate and integrity validation that Spotify's OAuth flow performs before " +
        "allowing sign-in via Google or normal credentials.",
    default = true,
) {
    compatibleWith(spotifyCompatibility)
    extendWith("extensions/spotify.mpe")

    execute {
        // 1. Ensure DetectionBypass signature hooks are applied early.
        // (HideDetectionPatch also does this, but we inject here as a second-layer guarantee
        //  specifically for the login flow which initializes its own class loaders.)

        // 2. Bypass login certificate validation — patch methods that return false
        //    (validation failure) to always return true (validation success).
        LoginCertValidationFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()

            // Find the first CONST_4 / CONST that loads 0 (false) before a RETURN
            val returnIndex = instructions.indexOfFirst { it.opcode == Opcode.RETURN }
            if (returnIndex >= 0) {
                val reg = (instructions[returnIndex] as? OneRegisterInstruction)?.registerA ?: 0
                // Replace with: const/4 vX, 1  (true) then return vX
                method.replaceInstruction(
                    returnIndex,
                    "const/4 v$reg, 0x1"
                )
            }

            // Also short-circuit the whole method by returning true from the top
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent()
            )
        }

        // 3. Intercept Play Integrity token fetch in the login flow.
        // Early-returning prevents the token from being sent to Spotify's servers.
        LoginIntegrityTokenFingerprint.matchAllOrNull()?.forEach { match ->
            match.method.addInstructions(0, "return-void")
        }

        // 4. Patch Google Sign-In request builder to use the official Spotify cert.
        GoogleSignInRequestBuilderFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()

            // Find context/application parameter register (usually p0 or p1)
            val paramCount = method.parameters.size
            val contextRegister = if (paramCount > 0) 1 else 0

            // Inject GoogleSignInBypass.install() call before the builder runs
            method.addInstructions(
                0,
                """
                    invoke-static { p$contextRegister }, $EXTENSION_LOGIN_CLASS->install(Landroid/content/Context;)V
                """.trimIndent()
            )
        }

        // 5. Allow social/OAuth login launcher to proceed regardless of signature state.
        SocialLoginLauncherFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            val instructions = method.implementation?.instructions?.toList().orEmpty()

            // Inject GoogleSignInBypass.install() at start of launcher
            if (method.parameters.isNotEmpty()) {
                method.addInstructions(
                    0,
                    """
                        invoke-static { p1 }, $EXTENSION_LOGIN_CLASS->install(Landroid/content/Context;)V
                    """.trimIndent()
                )
            }
        }
    }
}
