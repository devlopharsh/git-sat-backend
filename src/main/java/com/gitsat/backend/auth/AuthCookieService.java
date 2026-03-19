package com.gitsat.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    private final String cookieName;
    private final long maxAgeSeconds;
    private final boolean secureCookie;
    private final String sameSite;

    public AuthCookieService(
            @Value("${auth.cookie.name:git-sat}") String cookieName,
            @Value("${auth.cookie.max-age-seconds:604800}") long maxAgeSeconds,
            @Value("${auth.cookie.secure:false}") boolean secureCookie,
            @Value("${auth.cookie.same-site:Lax}") String sameSite
    ) {
        this.cookieName = cookieName;
        this.maxAgeSeconds = maxAgeSeconds;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public void writeAuthCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, maxAgeSeconds));
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0));
    }

    public String getCookieName() {
        return cookieName;
    }

    private String buildCookie(String value, long maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
