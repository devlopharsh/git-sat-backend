package com.gitsat.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String SESSION_TOKEN_TYPE = "session";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long sessionTokenTtlSeconds;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtUtil(
            ObjectMapper objectMapper,
            @Value("${auth.token.secret:}") String configuredSecret,
            @Value("${auth.session-token.ttl-seconds:604800}") long sessionTokenTtlSeconds,
            @Value("${auth.access-token.ttl-seconds:900}") long accessTokenTtlSeconds,
            @Value("${auth.refresh-token.ttl-seconds:2592000}") long refreshTokenTtlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = resolveSecret(configuredSecret);
        this.sessionTokenTtlSeconds = sessionTokenTtlSeconds;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String generateSessionToken(AuthenticatedUser user) {
        return generateToken(user, SESSION_TOKEN_TYPE, sessionTokenTtlSeconds);
    }

    public String generateAccessToken(AuthenticatedUser user) {
        return generateToken(user, ACCESS_TOKEN_TYPE, accessTokenTtlSeconds);
    }

    public String generateRefreshToken(AuthenticatedUser user) {
        return generateToken(user, REFRESH_TOKEN_TYPE, refreshTokenTtlSeconds);
    }

    public AuthenticatedUser parseSessionToken(String token) {
        return parseToken(token, SESSION_TOKEN_TYPE);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        return parseToken(token, ACCESS_TOKEN_TYPE);
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        return parseToken(token, REFRESH_TOKEN_TYPE);
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private String generateToken(AuthenticatedUser user, String tokenType, long ttlSeconds) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + ttlSeconds;

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.email());
        payload.put("uid", user.userId());
        payload.put("name", user.name());
        payload.put("token_type", tokenType);
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String encodedHeader = base64UrlJson(header);
        String encodedPayload = base64UrlJson(payload);
        String signature = sign(encodedHeader + "." + encodedPayload);
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    private AuthenticatedUser parseToken(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, MAP_TYPE);

            long expiresAt = asLong(payload.get("exp"));
            if (Instant.now().getEpochSecond() >= expiresAt) {
                return null;
            }

            String tokenType = asString(payload.get("token_type"));
            if (!expectedType.equals(tokenType)) {
                return null;
            }

            String userId = asString(payload.get("uid"));
            String email = asString(payload.get("sub"));
            String name = asString(payload.get("name"));
            if (userId.isBlank() || email.isBlank() || name.isBlank()) {
                return null;
            }
            return new AuthenticatedUser(userId, email, name);
        } catch (IllegalArgumentException | IOException ex) {
            return null;
        }
    }

    private byte[] resolveSecret(String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            return configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        logger.warn("AUTH_TOKEN_SECRET is not configured. Generated an in-memory signing secret; auth cookies and CLI tokens will reset on restart.");
        return generated;
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize token payload.", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign auth token.", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            diff |= leftBytes[i] ^ rightBytes[i];
        }
        return diff == 0;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        return 0L;
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
