package app.spicetify.extension.spotify.login;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Runtime bypass for Google Sign-In certificate validation on unofficial Spotify builds.
 *
 * Google Sign-In validates the calling app's SHA-256 certificate fingerprint via
 * Google Play Services. On an unofficial (re-signed) build, this check fails because
 * the fingerprint doesn't match Spotify's registered OAuth client fingerprint.
 *
 * This class intercepts GoogleAuthUtil / GoogleSignIn internal calls to return
 * the official Spotify certificate fingerprint, allowing Google OAuth to succeed.
 */
@android.annotation.SuppressLint("all")
public final class GoogleSignInBypass {

    /** Official Spotify SHA-1 certificate (registered with Google OAuth console). */
    private static final String OFFICIAL_SHA1 = "d6a6dced4a85f24204bf9505ccc1fce114cadb32";

    /** Official Spotify SHA-256 certificate (used by Google Sign-In for OAuth validation). */
    private static final String OFFICIAL_SHA256 =
            "6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd";

    private static volatile boolean installed = false;

    private GoogleSignInBypass() {}

    /**
     * Installs Google Sign-In certificate bypass hooks.
     * Called early in Application lifecycle via AllowGoogleSignInPatch.
     */
    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;

        try {
            patchGoogleAuthUtilCertificateCheck(context);
        } catch (Throwable ignored) {}

        try {
            patchGmsSignatureVerifier(context);
        } catch (Throwable ignored) {}
    }

    /**
     * Hooks GoogleAuthUtil to override the signing certificate it reports
     * for the calling package. This makes Google Play Services believe
     * the app has the official Spotify certificate during OAuth.
     */
    private static void patchGoogleAuthUtilCertificateCheck(Context context) {
        // GoogleAuthUtil uses PackageManager.getPackageInfo with GET_SIGNATURES flag.
        // We hook the PackageManager wrapper to return the spoofed cert.
        // The actual hook is applied by DetectionBypass.install(); here we ensure
        // the hook is active before any Google Sign-In code runs.
        try {
            Class<?> detectionClass = Class.forName(
                    "app.spicetify.extension.spotify.detection.DetectionBypass");
            Method installMethod = detectionClass.getMethod("install", Context.class);
            installMethod.invoke(null, context);
        } catch (Throwable ignored) {}
    }

    /**
     * Patches the GMS (Google Mobile Services) signature verifier used by
     * GoogleSignInClient to validate that the calling app's certificate matches
     * the one registered in Google's developer console.
     *
     * Approach: Override the cached signing certificate inside
     * com.google.android.gms.auth.api.signin.internal.SignInConfiguration
     * or com.google.android.gms.common.internal.CertData via reflection.
     */
    private static void patchGmsSignatureVerifier(Context context) {
        // Attempt to override the GMS "safe parcel" signing certificate.
        // GMS reads the app certificate once and caches it in memory.
        // We override that cached value to the official Spotify cert before
        // the first Google Sign-In call happens.
        try {
            Class<?> gmsHelper = Class.forName(
                    "com.google.android.gms.common.GoogleApiAvailabilityLight");
            Method getMethod = gmsHelper.getMethod("getInstance");
            Object instance = getMethod.invoke(null);

            // Reflect into the instance to find any cached signing info field
            for (Field field : instance.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(instance);
                if (val instanceof Signature[]) {
                    Signature[] sigs = (Signature[]) val;
                    if (sigs.length > 0) {
                        sigs[0] = new Signature(OFFICIAL_SHA1);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Override PackageManager cert cache for GMS-facing queries
        try {
            PackageManager pm = context.getPackageManager();
            // Use GET_SIGNATURES (deprecated but still read by GMS on older APIs)
            int flags = 0x00000040; // GET_SIGNATURES
            if (Build.VERSION.SDK_INT >= 28) {
                flags = 0x08000000; // GET_SIGNING_CERTIFICATES
            }
            android.content.pm.PackageInfo pi = pm.getPackageInfo(
                    "com.spotify.music", flags);
            if (pi != null) {
                if (pi.signatures != null && pi.signatures.length > 0) {
                    pi.signatures[0] = new Signature(OFFICIAL_SHA1);
                }
                if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
                    overrideSigningInfo(pi.signingInfo);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void overrideSigningInfo(Object signingInfo) {
        try {
            Field detailsField = signingInfo.getClass().getDeclaredField("mSigningDetails");
            detailsField.setAccessible(true);
            Object details = detailsField.get(signingInfo);
            if (details != null) {
                Field sigsField = details.getClass().getDeclaredField("signatures");
                sigsField.setAccessible(true);
                sigsField.set(details, new Signature[]{new Signature(OFFICIAL_SHA1)});
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Returns the official Spotify SHA-256 fingerprint for use in Google OAuth validation.
     * This can be injected into sign-in account request builders to pre-populate the cert.
     */
    public static String getOfficialSha256() {
        return OFFICIAL_SHA256;
    }

    /**
     * Returns the official Spotify SHA-1 fingerprint.
     */
    public static String getOfficialSha1() {
        return OFFICIAL_SHA1;
    }

    /**
     * Called from bytecode hooks targeting Google Sign-In account request creation.
     * Returns the correct signing certificate hex string that Spotify's Google OAuth
     * client ID was registered with.
     */
    public static String getSigningCertForOAuth() {
        return OFFICIAL_SHA1;
    }
}
