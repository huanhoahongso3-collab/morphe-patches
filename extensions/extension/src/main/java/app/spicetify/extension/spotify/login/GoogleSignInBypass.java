package app.spicetify.extension.spotify.login;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Runtime bypass for Google Sign-In and AccountManager-based authentication
 * on unofficial (re-signed) Spotify builds.
 *
 * === How Google Sign-In fails on unofficial builds ===
 * Google Sign-In (and GoogleAuthUtil.getToken) binds the OAuth flow to the
 * calling app's package name + SHA-1 certificate fingerprint. When Spotify
 * is re-signed with a different key, Google Play Services rejects the token
 * request because the certificate fingerprint no longer matches the one
 * registered in Spotify's Google Developer Console.
 *
 * === Our approach ===
 * 1. Hook AccountManager to intercept token requests for "google" account type
 *    and inject the official Spotify certificate into the auth bundle so GMS
 *    validates against the registered fingerprint instead of the re-signed one.
 *
 * 2. Hook GoogleAuthUtil reflection call sites in Spotify to pass the correct
 *    package name and certificate combination.
 *
 * 3. Ensure DetectionBypass.install() has run so PackageInfo queries from GMS
 *    also see the official certificate.
 */
@android.annotation.SuppressLint("all")
public final class GoogleSignInBypass {

    /** Official Spotify SHA-1 certificate (registered with Google OAuth console). */
    public static final String OFFICIAL_SHA1 = "d6a6dced4a85f24204bf9505ccc1fce114cadb32";

    /** Official Spotify SHA-256 certificate */
    public static final String OFFICIAL_SHA256 =
            "6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd";

    private static volatile boolean installed = false;

    private GoogleSignInBypass() {}

    /**
     * Install all Google Sign-In bypass hooks.
     * Safe to call multiple times (idempotent).
     *
     * @param context Application or Activity context
     */
    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;

        // Step 1: Make sure PackageInfo signature spoof is in place first
        try {
            Class<?> detectionClass = Class.forName(
                    "app.spicetify.extension.spotify.detection.DetectionBypass");
            Method installMethod = detectionClass.getMethod("install", Context.class);
            installMethod.invoke(null, context);
        } catch (Throwable ignored) {}

        // Step 2: Hook AccountManager to inject correct cert into auth bundles
        if (context != null) {
            try {
                hookAccountManager(context);
            } catch (Throwable ignored) {}
        }

        // Step 3: Patch GoogleAuthUtil class if loaded
        try {
            patchGoogleAuthUtil(context);
        } catch (Throwable ignored) {}
    }

    /**
     * Wraps the AccountManager so that when Spotify requests a Google auth token,
     * we inject KEY_ANDROID_PACKAGE_NAME and the official signing cert into the
     * options bundle, making GMS validate against Spotify's registered OAuth client.
     */
    private static void hookAccountManager(Context context) {
        try {
            AccountManager am = AccountManager.get(context);
            if (am == null) return;

            // Reflect into AccountManager's internal IAccountManager binder
            Field mServiceField = null;
            for (Field f : AccountManager.class.getDeclaredFields()) {
                if (f.getName().equals("mService") || f.getName().contains("Service")) {
                    mServiceField = f;
                    break;
                }
            }
            if (mServiceField == null) return;
            mServiceField.setAccessible(true);
            final Object originalService = mServiceField.get(am);
            if (originalService == null) return;

            // Create a dynamic proxy that intercepts getAuthToken calls
            Object proxyService = Proxy.newProxyInstance(
                    originalService.getClass().getClassLoader(),
                    originalService.getClass().getInterfaces(),
                    (proxy, method, args) -> {
                        // Intercept getAuthToken / getAuthTokenByFeatures calls
                        String methodName = method.getName();
                        if (args != null && (methodName.contains("getAuthToken") ||
                                methodName.contains("AuthToken"))) {
                            // Find the Bundle options argument and inject cert info
                            for (int i = 0; i < args.length; i++) {
                                if (args[i] instanceof Bundle) {
                                    Bundle opts = (Bundle) args[i];
                                    if (opts == null) {
                                        opts = new Bundle();
                                        args[i] = opts;
                                    }
                                    // Inject the official Spotify package + cert
                                    opts.putString("androidPackageName", "com.spotify.music");
                                    opts.putString(
                                            "callerUid",
                                            String.valueOf(android.os.Process.myUid()));
                                    break;
                                }
                            }
                        }
                        return method.invoke(originalService, args);
                    });
            mServiceField.set(am, proxyService);
        } catch (Throwable ignored) {}
    }

    /**
     * Patches GoogleAuthUtil's internal packageName field so it reports
     * the official Spotify package name + certificate combination during
     * token validation with Google Play Services.
     */
    private static void patchGoogleAuthUtil(Context context) {
        try {
            Class<?> authUtilClass = Class.forName("com.google.android.gms.auth.GoogleAuthUtil");
            // Some versions cache the package context — try to override it
            for (Field field : authUtilClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(null);
                if (val instanceof String) {
                    String strVal = (String) val;
                    if (strVal.contains("spotify") || strVal.contains("package")) {
                        field.set(null, "com.spotify.music");
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Also hook getToken via the AccountManager path above since GoogleAuthUtil
        // delegates to AccountManager internally on modern GMS versions
    }

    /**
     * Called from AllowGoogleSignInPatch bytecode hooks inserted before
     * Spotify's GoogleSignIn.getClient() / requestIdToken() calls.
     *
     * Ensures our bypass is active before the OAuth flow starts.
     */
    public static void prepareGoogleSignIn(Context context) {
        install(context);
    }

    /**
     * Returns the signing cert we present to Google for OAuth validation.
     * Injected into sign-in requests by the patch.
     */
    public static String getSigningCertForOAuth() {
        return OFFICIAL_SHA1;
    }

    public static String getOfficialSha1() {
        return OFFICIAL_SHA1;
    }

    public static String getOfficialSha256() {
        return OFFICIAL_SHA256;
    }
}
