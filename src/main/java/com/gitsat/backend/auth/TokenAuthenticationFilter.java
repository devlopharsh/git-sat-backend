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

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AuthCookieService authCookieService;

    public TokenAuthenticationFilter(TokenService tokenService, AuthCookieService authCookieService) {
        this.tokenService = tokenService;
        this.authCookieService = authCookieService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = readTokenFromCookies(request.getCookies());
        AuthenticatedUser user = tokenService.parseToken(token);

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

    private String readTokenFromCookies(Cookie[] cookies) {
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authCookieService.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
