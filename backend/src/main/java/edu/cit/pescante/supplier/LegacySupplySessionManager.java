package edu.cit.pescante.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Package-private session manager for LegacySupply.
 *
 * Caches the current session token. Proactively renews it when it is older than
 * PROACTIVE_REFRESH_SECONDS (180 s) since the measured TTL is ~240 s.
 * Also invalidates and renews immediately if a caller reports a 401 response.
 */
@Component
class LegacySupplySessionManager {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySessionManager.class);

    /** Proactively refresh the token after this many seconds (< measured TTL of ~240 s). */
    private static final long PROACTIVE_REFRESH_SECONDS = 180;

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile Instant tokenIssuedAt;

    LegacySupplySessionManager(
            @Value("${legacysupply.base-url}") String baseUrl,
            @Value("${legacysupply.client-id}") String clientId,
            @Value("${legacysupply.api-key:}") String apiKey) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Returns a valid session token, authenticating or re-authenticating as needed.
     *
     * @throws LegacySupplyException if authentication fails
     */
    synchronized String getToken() throws LegacySupplyException {
        if (isTokenFresh()) {
            return cachedToken;
        }
        return authenticate();
    }

    /**
     * Forces immediate re-authentication (call this when a 401 is received).
     */
    synchronized String invalidateAndRefresh() throws LegacySupplyException {
        log.info("[LS-SESSION] Invalidating session and re-authenticating");
        cachedToken = null;
        tokenIssuedAt = null;
        return authenticate();
    }

    private boolean isTokenFresh() {
        if (cachedToken == null || tokenIssuedAt == null) return false;
        long ageSeconds = Duration.between(tokenIssuedAt, Instant.now()).getSeconds();
        return ageSeconds < PROACTIVE_REFRESH_SECONDS;
    }

    private String authenticate() throws LegacySupplyException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LegacySupplyException("LS_API_KEY is not configured");
        }

        String body = "<AuthRequest>"
                + "<ClientId>" + clientId + "</ClientId>"
                + "<ApiKey>" + apiKey + "</ApiKey>"
                + "</AuthRequest>";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/token"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String errCode = LsXmlParser.extractTag(response.body(), "Code");
                throw new LegacySupplyException("Authentication failed [HTTP " + response.statusCode()
                        + " / " + errCode + "]: " + response.body());
            }
            String token = LsXmlParser.extractTag(response.body(), "SessionToken");
            if (token == null || token.isBlank()) {
                throw new LegacySupplyException("Empty session token in auth response: " + response.body());
            }
            cachedToken = token;
            tokenIssuedAt = Instant.now();
            log.info("[LS-SESSION] Authenticated. Token (first 10 chars): {}...", token.substring(0, Math.min(10, token.length())));
            return cachedToken;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LegacySupplyException("Auth HTTP call failed: " + e.getMessage(), e);
        }
    }
}
