package app.spicetify.extension.spotify.detection;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

public class DetectionBypassTest {

    @Test
    public void testOfficialConstants() {
        assertEquals("com.spotify.music", DetectionBypass.SPOTIFY_PACKAGE_NAME);
        assertEquals("com.android.vending", DetectionBypass.PLAY_STORE_INSTALLER);
        assertEquals("com.android.vending", DetectionBypass.getExpectedInstaller());
        assertEquals("d6a6dced4a85f24204bf9505ccc1fce114cadb32", DetectionBypass.OFFICIAL_SIGNATURE_SHA1);
        assertEquals("d6a6dced4a85f24204bf9505ccc1fce114cadb32", DetectionBypass.getExpectedSignatureSha1());
        assertEquals("6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd", DetectionBypass.OFFICIAL_SIGNATURE_SHA256);
        assertEquals("6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd", DetectionBypass.getExpectedSignatureSha256());
    }

    @Test
    public void testSpoofPackageInfoIgnoresOtherPackages() {
        PackageInfo otherApp = new PackageInfo();
        otherApp.packageName = "com.other.app";
        Signature originalSig = new Signature("1234567890abcdef");
        otherApp.signatures = new Signature[] { originalSig };

        DetectionBypass.spoofPackageInfo(otherApp);

        org.junit.Assert.assertSame(originalSig, otherApp.signatures[0]);
    }

    @Test
    public void testSpoofPackageInfoNullSafe() {
        DetectionBypass.spoofPackageInfo(null);
        PackageInfo emptyInfo = new PackageInfo();
        DetectionBypass.spoofPackageInfo(emptyInfo);
    }
}
