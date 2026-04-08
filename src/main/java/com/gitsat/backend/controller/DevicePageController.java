package com.gitsat.backend.controller;

import com.gitsat.backend.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DevicePageController {

    @GetMapping("/device")
    public String devicePage(Authentication authentication, HttpServletRequest request) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser)) {
            String next = request.getRequestURI();
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                next += "?" + request.getQueryString();
            }

            return "redirect:/login?message="
                    + urlEncode("Sign in to link this device.")
                    + "&next="
                    + urlEncode(next);
        }

        return "forward:/device.html";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
