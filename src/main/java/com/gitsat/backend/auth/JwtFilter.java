package com.gitsat.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuthCookieService authCookieService;
    private final AppUserService userService;

    public JwtFilter(
            JwtUtil jwtUtil,
            AuthCookieService authCookieService,
            AppUserService userService
    ) {
        this.jwtUtil = jwtUtil;
        this.authCookieService = authCookieService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        AuthenticatedUser candidateUser = readBearerToken(request);
        if (candidateUser == null) {
            candidateUser = readCookieToken(request.getCookies());
        }

        AuthenticatedUser user = null;
        if (candidateUser != null) {
            Optional<AuthenticatedUser> byId = userService.findAuthenticatedUserById(candidateUser.userId());
            if (byId.isPresent()) {
                user = byId.get();
            } else {
                user = userService.findAuthenticatedUserByEmail(candidateUser.email()).orElse(null);
            }
        }

        if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_USER")
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private AuthenticatedUser readBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtil.parseAccessToken(authorization.substring(7).trim());
    }

    private AuthenticatedUser readCookieToken(Cookie[] cookies) {
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authCookieService.getCookieName().equals(cookie.getName())) {
                AuthenticatedUser sessionUser = jwtUtil.parseSessionToken(cookie.getValue());
                if (sessionUser != null) {
                    return sessionUser;
                }
                return jwtUtil.parseAccessToken(cookie.getValue());
            }
        }
        return null;
    }
}
