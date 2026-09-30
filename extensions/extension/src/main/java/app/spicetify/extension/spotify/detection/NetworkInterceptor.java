package app.spicetify.extension.spotify.detection;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * OkHttp-compatible network interceptor that strips Spotify integrity/detection
 * tokens from outgoing HTTP requests before they reach Spotify's servers.
 */
@android.annotation.SuppressLint("all")
public final class NetworkInterceptor {

    private static final Set<String> SPOTIFY_HOSTS = new HashSet<>(Arrays.asList(
            "spclient.wg.spotify.com",
            "accounts.spotify.com",
            "api.spotify.com",
            "apresolve.spotify.com",
            "login5.spotify.com"
    ));

    private static final Set<String> STRIP_HEADERS = new HashSet<>(Arrays.asList(
            "X-Spotify-Integrity-Token",
            "X-Integrity-Token",
            "X-Play-Integrity-Token",
            "X-App-Integrity",
            "X-Client-Token",
            "X-Spotify-App-State",
            "X-Device-Attestation"
    ));

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
    private static Method proceedMethod = null;

    private NetworkInterceptor() {}

    public static Object intercept(Object chain) throws IOException {
        if (!enabled) {
            try {
                return proceedWithChain(chain, getRequest(chain));
            } catch (IOException ioe) {
                throw ioe;
            } catch (Throwable t) {
                throw new IOException("intercept failed", t);
            }
        }

        try {
            Object request = getRequest(chain);
            Object url = getUrl(request);

            String host = getHost(url);
            if (host != null && isSpotifyHost(host)) {
                request = stripIntegrityHeaders(request);
                request = stripIntegrityBodyFields(request);
            }

            return proceedWithChain(chain, request);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable e) {
            try {
                return proceedWithChain(chain, getRequest(chain));
            } catch (IOException ioe2) {
                throw ioe2;
            } catch (Throwable t) {
                throw new IOException("intercept fallback failed", t);
            }
        }
    }

    private static boolean isSpotifyHost(String host) {
        for (String spotifyHost : SPOTIFY_HOSTS) {
            if (host.equalsIgnoreCase(spotifyHost) || host.endsWith("." + spotifyHost)) {
                return true;
            }
        }
        return host.endsWith(".spotify.com") || host.equals("spotify.com");
    }

    private static Object stripIntegrityHeaders(Object request) {
        try {
            Object builder = request.getClass().getMethod("newBuilder").invoke(request);
            Class<?> builderClass = builder.getClass();

            for (String header : STRIP_HEADERS) {
                try {
                    builderClass.getMethod("removeHeader", String.class).invoke(builder, header);
                } catch (Throwable ignored) {}
            }

            try {
                Object headers = request.getClass().getMethod("headers").invoke(request);
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

    private static Object stripIntegrityBodyFields(Object request) {
        try {
            Object body = request.getClass().getMethod("body").invoke(request);
            if (body == null) return request;

            Object contentType = body.getClass().getMethod("contentType").invoke(body);
            if (contentType == null) return request;
            String contentTypeStr = contentType.toString();
            if (!contentTypeStr.contains("json") && !contentTypeStr.contains("application/x-www-form-urlencoded")) {
                return request;
            }

            try {
                Class<?> bufferClass = Class.forName("okio.Buffer");
                Object buffer = bufferClass.newInstance();
                body.getClass().getMethod("writeTo", bufferClass).invoke(body, buffer);
                String bodyStr = (String) bufferClass.getMethod("readUtf8").invoke(buffer);

                String cleaned = cleanJsonBody(bodyStr);
                if (cleaned.equals(bodyStr)) return request;

                Class<?> requestBodyClass = Class.forName("okhttp3.RequestBody");
                Class<?> mediaTypeClass = Class.forName("okhttp3.MediaType");
                Object mediaType = mediaTypeClass.getMethod("parse", String.class)
                        .invoke(null, contentTypeStr);
                Object newBody = requestBodyClass.getMethod("create", mediaTypeClass, String.class)
                        .invoke(null, mediaType, cleaned);

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

    static String cleanJsonBody(String json) {
        if (json == null || json.isEmpty()) return json;
        String result = json;
        for (String field : STRIP_JSON_FIELDS) {
            result = result.replaceAll("\"" + field + "\"\\s*:\\s*\"[^\"]*\"\\s*,?", "");
            result = result.replaceAll("\"" + field + "\"\\s*:\\s*null\\s*,?", "");
            result = result.replaceAll(",\\s*\"" + field + "\"\\s*:\\s*\"[^\"]*\"", "");
            result = result.replaceAll(",\\s*\"" + field + "\"\\s*:\\s*null", "");
        }
        return result;
    }

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
            if (proceedMethod == null) {
                for (Method m : chain.getClass().getMethods()) {
                    if (m.getName().equals("proceed") && m.getParameterTypes().length == 1) {
                        proceedMethod = m;
                        proceedMethod.setAccessible(true);
                        break;
                    }
                }
            }
            if (proceedMethod != null) {
                return proceedMethod.invoke(chain, request);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Chain proceed failed", cause);
        } catch (Throwable e) {
            throw new IOException("Chain proceed failed", e);
        }
        throw new IOException("Could not find proceed method on chain");
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
