package com.gitsat.backend;

import com.gitsat.backend.dto.SummaryRequest;
import com.gitsat.backend.dto.SummaryResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "runNvidiaApiTest", matches = "true")
class NvidiaApiSmokeTest {

    private static final String FALLBACK_SUMMARY = "Minor edits or formatting changes.";

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${NVIDIA_API_KEY:}")
    private String apiKey;

    @Test
    void summaryEndpointReturnsLiveSummaries() {
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "NVIDIA_API_KEY must be configured to run the smoke test.");

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
                                - return "todo";
                                + String requestBody = buildChatCompletionPayload(prompt);
                                + HttpRequest request = HttpRequest.newBuilder()
                                +         .uri(URI.create(apiBase + "/chat/completions"))
                                +         .header("Authorization", "Bearer " + apiKey)
                                +         .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                +         .build();
                                + return parseAssistantSummary(send(request));
                                """
                        ))
                ))
        );

        ResponseEntity<SummaryResponse> response =
                restTemplate.postForEntity("/summary", request, SummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().files()).isNotEmpty();
        assertThat(response.getBody().files().get(0).summary())
                .isNotBlank()
                .isNotEqualTo(FALLBACK_SUMMARY);
        assertThat(response.getBody().overall()).isNotBlank();
    }
}
