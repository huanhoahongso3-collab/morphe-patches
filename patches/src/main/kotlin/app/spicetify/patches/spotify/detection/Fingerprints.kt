package app.spicetify.patches.spotify.detection

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Matches Spotify's periodic integrity verification runner (Play Integrity / tamper checking).
 * Returning early prevents the app from generating or transmitting bad verdicts to Spotify servers.
 */
internal object IntegrityVerificationFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    strings = listOf("play_integrity", "integrity"),
)

/**
 * Matches Spotify's internal signature retrieval and package checking method.
 * Contains logging string "Failed to get the application signatures".
 */
internal object GetPackageInfoFingerprint : Fingerprint(
    strings = listOf("Failed to get the application signatures"),
)

/**
 * Matches attachBaseContext in the Spotify Application class to install early runtime hooks.
 */
internal object ApplicationAttachBaseContextFingerprint : Fingerprint(
    name = "attachBaseContext",
    parameters = listOf("Landroid/content/Context;"),
    returnType = "V",
)

/**
 * Matches onCreate in the Spotify Application class as a fallback hook injection point.
 */
internal object ApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "/SpotifyApplication;",
    name = "onCreate",
    parameters = emptyList(),
    returnType = "V",
)

/**
 * Matches Spotify's boolean signature/certificate validity checker used in the login flow.
 * Always returning true allows login to proceed even when the build is not officially signed.
 */
internal object SignatureValidityFingerprint : Fingerprint(
    strings = listOf("isValidSignature", "signature_valid", "cert_valid"),
    returnType = "Z",
)

/**
 * Matches Spotify's login session validator that verifies device integrity before
 * establishing a login session. Early-returning or short-circuiting prevents this
 * from blocking login on patched builds.
 */
internal object LoginSessionValidatorFingerprint : Fingerprint(
    strings = listOf("session_validator", "device_integrity", "login_blocked"),
    returnType = "V",
)
