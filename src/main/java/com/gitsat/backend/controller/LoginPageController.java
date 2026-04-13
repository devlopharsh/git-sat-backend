package com.gitsat.backend.controller;

import com.gitsat.backend.auth.AuthenticatedUser;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class LoginPageController {
    private static final String SETUP_FILE_NAME = "git-sat-setup-1.0.0.exe";

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

    @GetMapping("/download")
    public String downloadPage() {
        return "forward:/download.html";
    }

    @GetMapping("/download/setup")
    public ResponseEntity<Resource> downloadSetup() {
        Resource installer = resolveInstaller();

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(installer.contentLength())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(SETUP_FILE_NAME).build().toString()
                    )
                    .body(installer);
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unable to read installer.", exception);
        }
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

    private Resource resolveInstaller() {
        Resource classpathInstaller = new ClassPathResource("setup/" + SETUP_FILE_NAME);
        if (classpathInstaller.exists()) {
            return classpathInstaller;
        }

        Path filesystemInstaller = Paths.get("assets", "setup", SETUP_FILE_NAME).toAbsolutePath().normalize();
        Resource filesystemResource = new FileSystemResource(filesystemInstaller);
        if (filesystemResource.exists()) {
            return filesystemResource;
        }

        throw new ResponseStatusException(NOT_FOUND, "Installer not available.");
    }
}
