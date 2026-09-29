package app.spicetify.patches.spotify.login

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Matches Spotify's Google Sign-In account request builder — the method that
 * constructs a GoogleSignInAccount or GoogleSignInOptions with the app's certificate.
 * Returning early or replacing the cert allows Google OAuth to proceed on unofficial builds.
 */
internal object GoogleSignInRequestBuilderFingerprint : Fingerprint(
    strings = listOf("GoogleSignInOptions", "requestEmail"),
)

/**
 * Matches Spotify's OAuth / social login launcher that checks whether
 * the installed app is a known (signed) release before starting the OAuth flow.
 * Patching this allows login to start even when the signature check would fail.
 */
internal object SocialLoginLauncherFingerprint : Fingerprint(
    strings = listOf("google", "login", "oauth"),
    returnType = "V",
)

/**
 * Matches the method that validates the app certificate before sending credentials
 * to Spotify's auth server. Early-returning prevents the certificate rejection.
 */
internal object LoginCertValidationFingerprint : Fingerprint(
    strings = listOf("certificate", "signature", "login"),
    returnType = "Z",
)

/**
 * Matches the Play Integrity / SafetyNet token fetch method used specifically
 * in the login flow (separate from the general integrity check).
 */
internal object LoginIntegrityTokenFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("integrity_token", "play_integrity", "nonce"),
    returnType = "V",
)
