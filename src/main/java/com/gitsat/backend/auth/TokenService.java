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
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(
            ObjectMapper objectMapper,
            @Value("${auth.token.secret:}") String configuredSecret,
            @Value("${auth.token.ttl-seconds:604800}") long ttlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = resolveSecret(configuredSecret);
        this.ttlSeconds = ttlSeconds;
    }

    public String generateToken(AuthenticatedUser user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + ttlSeconds;

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.email());
        payload.put("name", user.name());
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String encodedHeader = base64UrlJson(header);
        String encodedPayload = base64UrlJson(payload);
        String signature = sign(encodedHeader + "." + encodedPayload);
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    public AuthenticatedUser parseToken(String token) {
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

            String email = asString(payload.get("sub"));
            String name = asString(payload.get("name"));
            if (email.isBlank() || name.isBlank()) {
                return null;
            }
            return new AuthenticatedUser(email, name);
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
        logger.warn("AUTH_TOKEN_SECRET is not configured. Generated an in-memory signing secret; auth cookies will reset on restart.");
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
