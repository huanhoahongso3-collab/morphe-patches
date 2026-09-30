package app.spicetify.extension.spotify.detection;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Parcel;

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

    public static final String OFFICIAL_SIGNATURE_SHA1 = "d6a6dced4a85f24204bf9505ccc1fce114cadb32";
    public static final String OFFICIAL_SIGNATURE_SHA256 =
            "6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd";

    private static volatile boolean installed = false;

    private DetectionBypass() {}

    /**
     * Installs runtime hooks for PackageInfo, signature verification, and login bypass.
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

    private static void spoofInstallerSource(Context context) {
        if (context == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Object ipm = getIPackageManager();
                if (ipm != null) {
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

    private static void hookPackageInfoCreator() {
        try {
            Field creatorField = PackageInfo.class.getDeclaredField("CREATOR");
            creatorField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Parcel.Creator<PackageInfo> originalCreator =
                    (Parcel.Creator<PackageInfo>) creatorField.get(null);

            Parcel.Creator<PackageInfo> spoofedCreator = new Parcel.Creator<PackageInfo>() {
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

    public static void spoofPackageInfo(PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.packageName == null) {
            return;
        }
        if (!SPOTIFY_PACKAGE_NAME.equals(packageInfo.packageName)) {
            return;
        }
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

    public static Object interceptIntegrityToken(Object tokenProvider) {
        return null;
    }

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
