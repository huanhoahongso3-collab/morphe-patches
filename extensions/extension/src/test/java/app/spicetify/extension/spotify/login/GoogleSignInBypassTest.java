package app.spicetify.extension.spotify.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GoogleSignInBypassTest {

    @Test
    public void testOfficialCertificateConstants() {
        assertEquals("d6a6dced4a85f24204bf9505ccc1fce114cadb32",
                GoogleSignInBypass.getOfficialSha1());
        assertEquals("6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd",
                GoogleSignInBypass.getOfficialSha256());
    }

    @Test
    public void testSigningCertForOAuthMatchesSha1() {
        // getSigningCertForOAuth() should return the official Spotify SHA-1
        // so Google OAuth can validate it matches the registered client
        assertEquals(GoogleSignInBypass.getOfficialSha1(),
                GoogleSignInBypass.getSigningCertForOAuth());
    }

    @Test
    public void testInstallIsNullSafe() {
        // install(null) should not throw
        GoogleSignInBypass.install(null);
    }
}
