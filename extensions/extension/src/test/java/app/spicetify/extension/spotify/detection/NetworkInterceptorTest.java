package app.spicetify.extension.spotify.detection;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkInterceptorTest {

    @Test
    public void testCleanJsonBodyRemovesIntegrityToken() {
        String json = "{\"username\":\"test\",\"integrityToken\":\"abc123def\",\"password\":\"pass\"}";
        String cleaned = NetworkInterceptor.cleanJsonBody(json);
        assertFalse("Should remove integrityToken field", cleaned.contains("integrityToken"));
        assertTrue("Should preserve username", cleaned.contains("username"));
        assertTrue("Should preserve password", cleaned.contains("password"));
    }

    @Test
    public void testCleanJsonBodyRemovesPlayIntegrityToken() {
        String json = "{\"play_integrity_token\":\"xyz789\",\"device_id\":\"abc\"}";
        String cleaned = NetworkInterceptor.cleanJsonBody(json);
        assertFalse("Should remove play_integrity_token", cleaned.contains("play_integrity_token"));
        assertTrue("Should preserve device_id", cleaned.contains("device_id"));
    }

    @Test
    public void testCleanJsonBodyHandlesNullValue() {
        String json = "{\"attestation_token\":null,\"user\":\"alice\"}";
        String cleaned = NetworkInterceptor.cleanJsonBody(json);
        assertFalse("Should remove null attestation_token", cleaned.contains("attestation_token"));
        assertTrue("Should preserve user", cleaned.contains("user"));
    }

    @Test
    public void testCleanJsonBodyHandlesEmptyAndNullInput() {
        assertEquals("", NetworkInterceptor.cleanJsonBody(""));
        assertNull(NetworkInterceptor.cleanJsonBody(null));
    }

    @Test
    public void testCleanJsonBodyNoModificationWhenNoTokens() {
        String json = "{\"username\":\"test\",\"device\":\"android\"}";
        String cleaned = NetworkInterceptor.cleanJsonBody(json);
        assertTrue("Should still contain username", cleaned.contains("username"));
        assertTrue("Should still contain device", cleaned.contains("device"));
    }
}
