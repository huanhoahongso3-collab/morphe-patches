package app.spicetify.extension.spotify.detection;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Runtime detection bypass helper for Spotify on Android.
 * Prevents Spotify from detecting app modifications by spoofing the application's
 * package info, signing certificates, installer source, and login integrity tokens.
 */
@android.annotation.SuppressLint("all")
public final class DetectionBypass {

    public static final String SPOTIFY_PACKAGE_NAME = "com.spotify.music";
    public static final String PLAY_STORE_INSTALLER = "com.android.vending";

    /**
     * Official Spotify release signing certificate hashes.
     * These match the certificates registered with Spotify's backend and Google Sign-In OAuth.
     */
    public static final String OFFICIAL_SIGNATURE_SHA1 = "d6a6dced4a85f24204bf9505ccc1fce114cadb32";
    public static final String OFFICIAL_SIGNATURE_SHA256 =
            "6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd";

    private static volatile boolean installed = false;
    private static volatile Signature officialSignature = null;

    private DetectionBypass() {}

    /**
     * Installs runtime hooks for PackageInfo, signature verification, and login bypass.
     * Should be called as early as possible during application initialization.
     *
     * @param context Application context
     */
    public static synchronized void install(Context context) {
        if (installed) {
            return;
        }
        installed = true;

        try {
            hookPackageInfoCreator();
        } catch (Throwable ignored) {}

        try {
            clearPackageManagerCache();
        } catch (Throwable ignored) {}

        try {
            spoofInstallerSource(context);
        } catch (Throwable ignored) {}
    }

    /**
     * Spoofs the installer package name at the PackageManager level so that
     * Spotify's install-source checks see "com.android.vending" (Google Play Store).
     * This is required for login to succeed because Spotify's auth flow validates
     * whether the app was installed from the Play Store.
     */
    private static void spoofInstallerSource(Context context) {
        if (context == null) return;
        try {
            // For API 30+: use InstallSourceInfo spoof approach via reflection
            if (Build.VERSION.SDK_INT >= 30) {
                Object ipm = getIPackageManager();
                if (ipm != null) {
                    // Try to override getInstallSourceInfo if accessible
                    // (best-effort; caught silently if blocked)
                    try {
                        Method setMethod = ipm.getClass().getMethod(
                                "setInstallerPackageName",
                                String.class, String.class);
                        setMethod.setAccessible(true);
                        setMethod.invoke(ipm, SPOTIFY_PACKAGE_NAME, PLAY_STORE_INSTALLER);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Object getIPackageManager() {
        try {
            Class<?> amClass = Class.forName("android.app.ActivityThread");
            Method currentMethod = amClass.getMethod("currentActivityThread");
            Object activityThread = currentMethod.invoke(null);
            Method getPmMethod = activityThread.getClass().getMethod("getPackageManager");
            return getPmMethod.invoke(activityThread);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Replaces PackageInfo.CREATOR so any unmarshalled PackageInfo for Spotify
     * has its signatures and installer spoofed to the authentic Google Play release.
     */
    private static void hookPackageInfoCreator() {
        try {
            Field creatorField = PackageInfo.class.getDeclaredField("CREATOR");
            creatorField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Parcelable.Creator<PackageInfo> originalCreator =
                    (Parcelable.Creator<PackageInfo>) creatorField.get(null);

            Parcelable.Creator<PackageInfo> spoofedCreator = new Parcelable.Creator<PackageInfo>() {
                @Override
                public PackageInfo createFromParcel(Parcel source) {
                    PackageInfo packageInfo = originalCreator.createFromParcel(source);
                    spoofPackageInfo(packageInfo);
                    return packageInfo;
                }

                @Override
                public PackageInfo[] newArray(int size) {
                    return originalCreator.newArray(size);
                }
            };

            creatorField.set(null, spoofedCreator);
            clearParcelCreators();
        } catch (Throwable ignored) {}
    }

    /**
     * Modifies the given PackageInfo instance in-place if it targets Spotify.
     */
    public static void spoofPackageInfo(PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.packageName == null) {
            return;
        }
        if (!SPOTIFY_PACKAGE_NAME.equals(packageInfo.packageName)) {
            return;
        }

        try {
            Signature sig = getOfficialSignature();
            if (sig != null) {
                if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                    packageInfo.signatures[0] = sig;
                } else if (packageInfo.signatures == null) {
                    packageInfo.signatures = new Signature[]{sig};
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (packageInfo.signingInfo != null) {
                        spoofSigningInfo(packageInfo.signingInfo, sig);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void spoofSigningInfo(SigningInfo signingInfo, Signature signature) {
        try {
            Field signingDetailsField = SigningInfo.class.getDeclaredField("mSigningDetails");
            signingDetailsField.setAccessible(true);
            Object signingDetails = signingDetailsField.get(signingInfo);
            if (signingDetails != null) {
                Field signaturesField = signingDetails.getClass().getDeclaredField("signatures");
                signaturesField.setAccessible(true);
                signaturesField.set(signingDetails, new Signature[] { signature });
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Creates or retrieves the official Spotify Signature instance.
     */
    public static Signature getOfficialSignature() {
        if (officialSignature != null) {
            return officialSignature;
        }
        try {
            officialSignature = new Signature(OFFICIAL_SIGNATURE_SHA1);
        } catch (Throwable ignored) {}
        return officialSignature;
    }

    private static void clearPackageManagerCache() {
        try {
            Field cacheField = PackageManager.class.getDeclaredField("sPackageInfoCache");
            cacheField.setAccessible(true);
            Object cache = cacheField.get(null);
            if (cache != null) {
                Method clearMethod = cache.getClass().getMethod("clear");
                clearMethod.invoke(cache);
            }
        } catch (Throwable ignored) {}
    }

    private static void clearParcelCreators() {
        try {
            Field mCreatorsField = Parcel.class.getDeclaredField("mCreators");
            mCreatorsField.setAccessible(true);
            Map<?, ?> mCreators = (Map<?, ?>) mCreatorsField.get(null);
            if (mCreators != null) {
                mCreators.clear();
            }
        } catch (Throwable ignored) {}

        try {
            Field sPairedCreatorsField = Parcel.class.getDeclaredField("sPairedCreators");
            sPairedCreatorsField.setAccessible(true);
            Map<?, ?> sPairedCreators = (Map<?, ?>) sPairedCreatorsField.get(null);
            if (sPairedCreators != null) {
                sPairedCreators.clear();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Intercept Play Integrity token calls — replaces the verdict with a noop/empty
     * value so the token is never sent to Spotify's server-side integrity endpoint.
     * Called from bytecode hooks injected by HideDetectionPatch.
     */
    public static Object interceptIntegrityToken(Object tokenProvider) {
        // Return null so callers cannot send a real attestation verdict.
        // Spotify's integrity check methods handle null gracefully (no-crash, no-report).
        return null;
    }

    /**
     * Returns whether the installed signature matches the official Spotify signature.
     * Always returns true so internal signature checks pass.
     */
    public static boolean isSignatureValid() {
        return true;
    }

    public static String getExpectedInstaller() {
        return PLAY_STORE_INSTALLER;
    }

    public static String getExpectedSignatureSha1() {
        return OFFICIAL_SIGNATURE_SHA1;
    }

    public static String getExpectedSignatureSha256() {
        return OFFICIAL_SIGNATURE_SHA256;
    }
}
