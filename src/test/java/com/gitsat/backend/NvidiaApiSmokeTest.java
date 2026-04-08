package com.gitsat.backend;

import com.gitsat.backend.auth.AppUser;
import com.gitsat.backend.auth.AppUserRepository;
import com.gitsat.backend.auth.DeviceCodeRepository;
import com.gitsat.backend.dto.AuthResponse;
import com.gitsat.backend.dto.RegisterRequest;
import com.gitsat.backend.dto.SummaryRequest;
import com.gitsat.backend.dto.SummaryResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration")
@EnabledIfSystemProperty(named = "runNvidiaApiTest", matches = "true")
class NvidiaApiSmokeTest {

    private static final String FALLBACK_SUMMARY = "Minor edits or formatting changes.";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private DeviceCodeRepository deviceCodeRepository;

    @Value("${NVIDIA_API_KEY:}")
    private String apiKey;

    private Map<String, AppUser> usersById;
    private Map<String, AppUser> usersByEmail;

    @BeforeEach
    void setUpRepository() {
        usersById = new ConcurrentHashMap<>();
        usersByEmail = new ConcurrentHashMap<>();

        when(appUserRepository.findByEmail(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));

        when(appUserRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));

        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser user = invocation.getArgument(0);
                    AppUser persistedUser = user.id() == null
                            ? new AppUser(
                            UUID.randomUUID().toString(),
                            user.email(),
                            user.name(),
                            user.passwordHash(),
                            user.createdAt(),
                            user.lastLoginAt()
                    )
                            : user;
                    usersById.put(persistedUser.id(), persistedUser);
                    usersByEmail.put(persistedUser.email(), persistedUser);
                    return persistedUser;
                });

        when(deviceCodeRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(0L);
    }

    @Test
    void summaryEndpointReturnsLiveSummaries() {
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "NVIDIA_API_KEY must be configured to run the smoke test.");

        RegisterRequest registerRequest = new RegisterRequest(
                "Smoke User",
                "smoke@example.com",
                "password123"
        );
        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/auth/register", registerRequest, AuthResponse.class);

        SummaryRequest request = new SummaryRequest(
                "git-sat-backend",
                "2026-03-01",
                "smoke-test",
                List.of(new SummaryRequest.CommitDto(
                        "smoke123",
                        "Add API-based summary generation for backend diff processing",
                        "2026-03-16T10:00:00Z",
                        List.of(new SummaryRequest.FileChangeDto(
                                "src/main/java/com/gitsat/backend/llm/LlmClient.java",
                                42,
                                7,
                                """
                                @@
                                - return \"todo\";
                                + String requestBody = buildChatCompletionPayload(prompt);
                                + HttpRequest request = HttpRequest.newBuilder()
                                +         .uri(URI.create(apiBase + \"/chat/completions\"))
                                +         .header(\"Authorization\", \"Bearer \" + apiKey)
                                +         .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                +         .build();
                                + return parseAssistantSummary(send(request));
                                """
                        ))
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, extractCookie(registerResponse));
        ResponseEntity<SummaryResponse> response = restTemplate.exchange(
                "/summary",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                SummaryResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().files()).isNotEmpty();
        assertThat(response.getBody().files().get(0).summary())
                .isNotBlank()
                .isNotEqualTo(FALLBACK_SUMMARY);
        assertThat(response.getBody().overall()).isNotBlank();
    }

    private String extractCookie(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotBlank();
        return header.split(";", 2)[0];
    }
}


