package app.spicetify.extension.spotify.detection;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * OkHttp-compatible network interceptor that strips Spotify integrity/detection
 * tokens from outgoing HTTP requests before they reach Spotify's servers.
 *
 * Spotify sends Play Integrity API verdicts and tamper-check tokens to its
 * authentication and streaming backends. This interceptor removes those headers
 * and JSON body fields so the server never receives a "FAILS_BASIC_INTEGRITY"
 * verdict from a modified client.
 *
 * Integration: This class is invoked via reflection from OkHttpClientPatch,
 * which hooks the OkHttpClient.Builder used by Spotify's networking layer.
 */
@android.annotation.SuppressLint("all")
public final class NetworkInterceptor {

    private static final String TAG = "SpicetifyNet";

    /** Spotify backend hostnames that receive integrity tokens */
    private static final Set<String> SPOTIFY_HOSTS = new HashSet<>(Arrays.asList(
            "spclient.wg.spotify.com",
            "accounts.spotify.com",
            "api.spotify.com",
            "apresolve.spotify.com",
            "login5.spotify.com"
    ));

    /** Request headers Spotify sends that contain integrity/detection info */
    private static final Set<String> STRIP_HEADERS = new HashSet<>(Arrays.asList(
            "X-Spotify-Integrity-Token",
            "X-Integrity-Token",
            "X-Play-Integrity-Token",
            "X-App-Integrity",
            "X-Client-Token",       // can contain build fingerprint
            "X-Spotify-App-State",  // may include tamper flags
            "X-Device-Attestation"
    ));

    /**
     * JSON body field names that carry integrity/attestation data.
     * These are stripped from POST/PUT request bodies before sending.
     */
    private static final Set<String> STRIP_JSON_FIELDS = new HashSet<>(Arrays.asList(
            "integrityToken",
            "integrity_token",
            "playIntegrityToken",
            "play_integrity_token",
            "attestationToken",
            "attestation_token",
            "deviceAttestation",
            "device_attestation",
            "safetyNetToken",
            "safety_net_token"
    ));

    private static volatile boolean enabled = true;

    private NetworkInterceptor() {}

    /**
     * Called via reflection from Spotify's OkHttp interceptor chain.
     * Strips integrity-related headers from the request before forwarding.
     *
     * This method uses duck-typing via reflection to work without a compile-time
     * dependency on OkHttp (which is bundled inside Spotify's APK).
     *
     * @param chain The OkHttp Interceptor.Chain object
     * @return The response from proceeding with the cleaned request
     */
    public static Object intercept(Object chain) throws IOException {
        if (!enabled) {
            return proceedWithChain(chain, getRequest(chain));
        }

        try {
            Object request = getRequest(chain);
            Object url = getUrl(request);

            // Only modify requests to known Spotify hosts
            String host = getHost(url);
            if (host != null && isSpotifyHost(host)) {
                request = stripIntegrityHeaders(request);
                request = stripIntegrityBodyFields(request);
            }

            return proceedWithChain(chain, request);
        } catch (Throwable e) {
            // On any error, proceed with original request unmodified
            return proceedWithChain(chain, getRequest(chain));
        }
    }

    private static boolean isSpotifyHost(String host) {
        for (String spotifyHost : SPOTIFY_HOSTS) {
            if (host.equalsIgnoreCase(spotifyHost) || host.endsWith("." + spotifyHost)) {
                return true;
            }
        }
        // Also match any *.spotify.com subdomain
        return host.endsWith(".spotify.com") || host.equals("spotify.com");
    }

    /**
     * Removes integrity-related headers from the OkHttp Request.
     * Uses reflection to call Request.newBuilder(), removeHeader(), and build().
     */
    private static Object stripIntegrityHeaders(Object request) {
        try {
            Object builder = request.getClass().getMethod("newBuilder").invoke(request);
            Class<?> builderClass = builder.getClass();

            for (String header : STRIP_HEADERS) {
                try {
                    builderClass.getMethod("removeHeader", String.class).invoke(builder, header);
                } catch (Throwable ignored) {}
            }

            // Also strip any X-Spotify-* headers that might carry detection info
            try {
                Object headers = request.getClass().getMethod("headers").invoke(request);
                // Get all header names
                java.util.List<?> names = (java.util.List<?>) headers.getClass()
                        .getMethod("names").invoke(headers);
                for (Object name : names) {
                    String headerName = (String) name;
                    if (headerName != null && (
                            headerName.toLowerCase().contains("integrity") ||
                            headerName.toLowerCase().contains("attestation") ||
                            headerName.toLowerCase().contains("safenet") ||
                            headerName.toLowerCase().contains("safety"))) {
                        try {
                            builderClass.getMethod("removeHeader", String.class)
                                    .invoke(builder, headerName);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            return builderClass.getMethod("build").invoke(builder);
        } catch (Throwable ignored) {
            return request;
        }
    }

    /**
     * Strips integrity-related fields from JSON request bodies.
     * Uses simple string replacement to avoid needing a JSON parser dependency.
     */
    private static Object stripIntegrityBodyFields(Object request) {
        try {
            Object body = request.getClass().getMethod("body").invoke(request);
            if (body == null) return request;

            // Check content type is JSON
            Object contentType = body.getClass().getMethod("contentType").invoke(body);
            if (contentType == null) return request;
            String contentTypeStr = contentType.toString();
            if (!contentTypeStr.contains("json") && !contentTypeStr.contains("application/x-www-form-urlencoded")) {
                return request;
            }

            // Read body as string
            okio_buffer_approach:
            try {
                // Use okio Buffer to read the body
                Class<?> bufferClass = Class.forName("okio.Buffer");
                Object buffer = bufferClass.newInstance();
                body.getClass().getMethod("writeTo", bufferClass).invoke(body, buffer);
                String bodyStr = (String) bufferClass.getMethod("readUtf8").invoke(buffer);

                // Strip JSON fields
                String cleaned = cleanJsonBody(bodyStr);
                if (cleaned.equals(bodyStr)) return request; // nothing changed

                // Reconstruct body
                Class<?> requestBodyClass = Class.forName("okhttp3.RequestBody");
                Class<?> mediaTypeClass = Class.forName("okhttp3.MediaType");
                Object mediaType = mediaTypeClass.getMethod("parse", String.class)
                        .invoke(null, contentTypeStr);
                Object newBody = requestBodyClass.getMethod("create", mediaTypeClass, String.class)
                        .invoke(null, mediaType, cleaned);

                // Build new request with new body
                Object builder = request.getClass().getMethod("newBuilder").invoke(request);
                String method = (String) request.getClass().getMethod("method").invoke(request);
                builder.getClass()
                        .getMethod("method", String.class, requestBodyClass)
                        .invoke(builder, method, newBody);
                return builder.getClass().getMethod("build").invoke(builder);
            } catch (Throwable ignored) {}

        } catch (Throwable ignored) {}
        return request;
    }

    /**
     * Removes integrity token fields from a JSON string using simple regex-like replacement.
     * Handles both "field": "value" and "field": null patterns.
     */
    static String cleanJsonBody(String json) {
        if (json == null || json.isEmpty()) return json;
        String result = json;
        for (String field : STRIP_JSON_FIELDS) {
            // Match both camelCase and snake_case, quoted string value or null
            result = result.replaceAll(
                    "\"" + field + "\"\\s*:\\s*\"[^\"]*\"\\s*,?", "");
            result = result.replaceAll(
                    "\"" + field + "\"\\s*:\\s*null\\s*,?", "");
            result = result.replaceAll(
                    ",\\s*\"" + field + "\"\\s*:\\s*\"[^\"]*\"", "");
            result = result.replaceAll(
                    ",\\s*\"" + field + "\"\\s*:\\s*null", "");
        }
        return result;
    }

    // ---- Reflection helpers ----

    private static Object getRequest(Object chain) throws Exception {
        return chain.getClass().getMethod("request").invoke(chain);
    }

    private static Object getUrl(Object request) throws Exception {
        return request.getClass().getMethod("url").invoke(request);
    }

    private static String getHost(Object url) {
        try {
            return (String) url.getClass().getMethod("host").invoke(url);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object proceedWithChain(Object chain, Object request) throws IOException {
        try {
            return chain.getClass().getMethod("proceed",
                    request.getClass().getSuperclass() != null
                            ? Object.class : request.getClass())
                    .invoke(chain, request);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Chain proceed failed", cause);
        } catch (Throwable e) {
            throw new IOException("Chain proceed failed", e);
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
