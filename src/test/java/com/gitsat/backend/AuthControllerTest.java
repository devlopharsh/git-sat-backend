package com.gitsat.backend;

import com.gitsat.backend.dto.AuthResponse;
import com.gitsat.backend.dto.LoginRequest;
import com.gitsat.backend.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registerAndLoginIssueGitSatCookie() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Test User",
                "tester@example.com",
                "password123"
        );

        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("git-sat=")
                .contains("HttpOnly");
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().email()).isEqualTo("tester@example.com");

        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, extractCookie(registerResponse));
        ResponseEntity<AuthResponse> meResponse = restTemplate.exchange(
                "/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(meHeaders),
                AuthResponse.class
        );

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).isNotNull();
        assertThat(meResponse.getBody().email()).isEqualTo("tester@example.com");

        LoginRequest loginRequest = new LoginRequest("tester@example.com", "password123");
        ResponseEntity<AuthResponse> loginResponse =
                restTemplate.postForEntity("/auth/login", loginRequest, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("git-sat=")
                .contains("HttpOnly");
    }

    private String extractCookie(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotBlank();
        return header.split(";", 2)[0];
    }
}
