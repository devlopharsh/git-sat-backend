package com.gitsat.backend.controller;

import com.gitsat.backend.auth.AuthCookieService;
import com.gitsat.backend.auth.AuthenticatedUser;
import com.gitsat.backend.auth.InMemoryUserService;
import com.gitsat.backend.auth.TokenService;
import com.gitsat.backend.dto.AuthResponse;
import com.gitsat.backend.dto.LoginRequest;
import com.gitsat.backend.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final InMemoryUserService userService;
    private final TokenService tokenService;
    private final AuthCookieService authCookieService;

    public AuthController(
            InMemoryUserService userService,
            TokenService tokenService,
            AuthCookieService authCookieService
    ) {
        this.userService = userService;
        this.tokenService = tokenService;
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

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedUser user = userService.login(request);
        issueCookie(response, user);
        return ResponseEntity.ok(new AuthResponse(user.email(), user.name(), "Login successful."));
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
        String token = tokenService.generateToken(user);
        authCookieService.writeAuthCookie(response, token);
    }
}
