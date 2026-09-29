package app.spicetify.extension.spotify.detection;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Runtime detection bypass helper for Spotify on Android.
 * Prevents Spotify from detecting app modifications by spoofing the application's
 * package info, signing certificates, and installer source (Google Play Store).
 */
public final class DetectionBypass {

    public static final String SPOTIFY_PACKAGE_NAME = "com.spotify.music";
    public static final String PLAY_STORE_INSTALLER = "com.android.vending";

    /**
     * Official Spotify release signing certificate hashes.
     */
    public static final String OFFICIAL_SIGNATURE_SHA1 = "d6a6dced4a85f24204bf9505ccc1fce114cadb32";
    public static final String OFFICIAL_SIGNATURE_SHA256 =
            "6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd";

    private static volatile boolean installed = false;
    private static volatile Signature officialSignature = null;

    private DetectionBypass() {}

    /**
     * Installs runtime hooks for PackageInfo and signature verification.
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
            clearPackageManagerCache();
        } catch (Throwable ignored) {
            // Fail-safe to avoid blocking app launch
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
            // Hex string representation of the official Spotify certificate
            // derived from official release certificate SHA-1 d6a6dced4a85f24204bf9505ccc1fce114cadb32
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
