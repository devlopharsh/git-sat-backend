package com.gitsat.backend.controller;

import com.gitsat.backend.auth.AppUserService;
import com.gitsat.backend.auth.AuthCookieService;
import com.gitsat.backend.auth.AuthService;
import com.gitsat.backend.auth.AuthenticatedUser;
import com.gitsat.backend.auth.JwtUtil;
import com.gitsat.backend.dto.ActivateDeviceRequest;
import com.gitsat.backend.dto.AuthResponse;
import com.gitsat.backend.dto.DeviceActivationResponse;
import com.gitsat.backend.dto.DeviceCodeResponse;
import com.gitsat.backend.dto.DeviceVerificationRequest;
import com.gitsat.backend.dto.DeviceVerificationResponse;
import com.gitsat.backend.dto.LoginRequest;
import com.gitsat.backend.dto.RefreshTokenRequest;
import com.gitsat.backend.dto.RefreshTokenResponse;
import com.gitsat.backend.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String LOGIN_FAILURE_MESSAGE =
            "We couldn't sign you in. Create an account to continue, or go back and retry your password.";

    private final AppUserService userService;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AuthCookieService authCookieService;

    public AuthController(
            AppUserService userService,
            AuthService authService,
            JwtUtil jwtUtil,
            AuthCookieService authCookieService
    ) {
        this.userService = userService;
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        return registerAndIssueCookie(request, response);
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        return registerAndIssueCookie(request, response);
    }

    @PostMapping(value = "/signup-form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> signupForm(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String next,
            HttpServletResponse response
    ) {
        try {
            RegisterRequest request = new RegisterRequest(name, email, password);
            AuthenticatedUser user = userService.register(request);
            issueCookie(response, user);
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", resolveSuccessLocation(next, "/signup?status=success"))
                    .build();
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", buildSignupPageLocation("error", ex.getReason(), next, name, email))
                    .build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedUser user = userService.login(request);
        issueCookie(response, user);
        return ResponseEntity.ok(new AuthResponse(user.email(), user.name(), "Login successful."));
    }

    @PostMapping(value = "/login-form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> loginForm(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String next,
            HttpServletResponse response
    ) {
        try {
            AuthenticatedUser user = userService.login(new LoginRequest(email, password));
            issueCookie(response, user);
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", resolveSuccessLocation(next, "/login?status=success"))
                    .build();
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", buildSignupPageLocation("info", LOGIN_FAILURE_MESSAGE, next, null, email))
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletResponse response) {
        authCookieService.clearAuthCookie(response);
        return ResponseEntity.ok(new AuthResponse("", "", "Logout successful."));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(new AuthResponse(user.email(), user.name(), "Authenticated."));
    }

    @PostMapping("/device-code")
    public ResponseEntity<DeviceCodeResponse> createDeviceCode() {
        return ResponseEntity.ok(authService.createDeviceCode());
    }

    @PostMapping("/activate-device")
    public ResponseEntity<DeviceActivationResponse> activateDevice(
            @RequestBody ActivateDeviceRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser user = authentication == null ? null : (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(authService.activateDevice(request.userCode(), user));
    }

    @PostMapping(value = "/activate-device", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> activateDeviceForm(
            @RequestParam(name = "user_code", required = false) String userCode,
            Authentication authentication
    ) {
        try {
            AuthenticatedUser user = authentication == null ? null : (AuthenticatedUser) authentication.getPrincipal();
            DeviceActivationResponse response = authService.activateDevice(userCode, user);
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", buildDevicePageLocation("success", response.message(), userCode))
                    .build();
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("Location", buildDevicePageLocation("error", ex.getReason(), userCode))
                    .build();
        }
    }

    @PostMapping("/verify-device-code")
    public ResponseEntity<DeviceVerificationResponse> verifyDeviceCode(@RequestBody DeviceVerificationRequest request) {
        DeviceVerificationResponse response = authService.verifyDeviceCode(request.deviceCode());
        return ResponseEntity.status(response.error() == null ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request.refreshToken()));
    }

    private ResponseEntity<AuthResponse> registerAndIssueCookie(
            RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedUser user = userService.register(request);
        issueCookie(response, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(user.email(), user.name(), "Registration successful."));
    }

    private void issueCookie(HttpServletResponse response, AuthenticatedUser user) {
        String token = jwtUtil.generateSessionToken(user);
        authCookieService.writeAuthCookie(response, token);
    }

    private String resolveSuccessLocation(String next, String fallback) {
        String safeNext = sanitizeLocalPath(next);
        if (safeNext != null) {
            return safeNext;
        }
        return fallback;
    }

    private String buildSignupPageLocation(
            String status,
            String message,
            String next,
            String name,
            String email
    ) {
        StringBuilder location = new StringBuilder("/signup?status=")
                .append(urlEncode(status));

        if (message != null && !message.isBlank()) {
            location.append("&message=").append(urlEncode(message));
        }

        String safeNext = sanitizeLocalPath(next);
        if (safeNext != null) {
            location.append("&next=").append(urlEncode(safeNext));
        }

        if (name != null && !name.isBlank()) {
            location.append("&name=").append(urlEncode(name));
        }

        if (email != null && !email.isBlank()) {
            location.append("&email=").append(urlEncode(email));
        }

        return location.toString();
    }

    private String buildDevicePageLocation(String status, String message, String userCode) {
        StringBuilder location = new StringBuilder("/device?status=")
                .append(urlEncode(status));

        if (message != null && !message.isBlank()) {
            location.append("&message=").append(urlEncode(message));
        }

        if (userCode != null && !userCode.isBlank()) {
            location.append("&user_code=").append(urlEncode(userCode));
        }

        return location.toString();
    }

    private String sanitizeLocalPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return null;
        }
        return candidate;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
