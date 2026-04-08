package com.gitsat.backend.controller;

import com.gitsat.backend.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginPageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(
            Authentication authentication,
            @RequestParam(required = false) String next
    ) {
        String safeNext = sanitizeLocalPath(next);
        if (safeNext != null && authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser) {
            return "redirect:" + safeNext;
        }
        return "forward:/login.html";
    }

    @GetMapping("/signup")
    public String signupPage(
            Authentication authentication,
            @RequestParam(required = false) String next
    ) {
        String safeNext = sanitizeLocalPath(next);
        if (safeNext != null && authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser) {
            return "redirect:" + safeNext;
        }
        return "forward:/signup.html";
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
}
