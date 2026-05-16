package com.gitsat.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitsat.backend.dto.SummaryRequest;
import com.gitsat.backend.dto.SummaryResponse;
import com.gitsat.backend.llm.LlmClient;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientTest {

    private final LlmClient llmClient = new LlmClient(
            new ObjectMapper(),
            "https://example.com/v1",
            "",
            "openai/gpt-oss-20b",
            1.0,
            1.0,
            256,
            0,
            500,
            1000
    );

    @Test
    void summarizeFileFallsBackWhenApiIsNotConfigured() {
        String summary = llmClient.summarizeFile(
                "src/Auth.java",
                8,
                2,
                List.of("Improve auth"),
                List.of("@@ diff")
        );

        assertThat(summary).isEqualTo("Minor edits or formatting changes.");
    }

    @Test
    void summaryMethodsReturnDeterministicFallbacksWithoutApiKey() {
        List<SummaryResponse.FileSummaryDto> files = List.of(
                new SummaryResponse.FileSummaryDto("src/Auth.java", 8, 2, "Adds auth validation."),
                new SummaryResponse.FileSummaryDto("src/Device.java", 4, 1, "Improves device activation.")
        );
        SummaryRequest request = new SummaryRequest("git-sat-backend", "2026-05-01", "harsh", List.of());

        assertThat(llmClient.summarizeOverall(files)).isEmpty();
        assertThat(llmClient.summarizeDetailedOverall(files))
                .contains("src/Auth.java: Adds auth validation.")
                .contains("src/Device.java: Improves device activation.");
        assertThat(llmClient.summarizeGoal(request, files))
                .isEqualTo("Improve git-sat-backend by delivering the functionality reflected in the recent code changes.");
        assertThat(llmClient.summarizeSuggestion(request, files, "Improve auth"))
                .isEqualTo("Add validation, edge-case handling, and automated tests to make this goal more reliable in real usage.");
        assertThat(llmClient.buildSuggestionPrompt(request, files, "Improve auth", "Add more tests"))
                .isEqualTo("Review the recent changes with the goal of Improve auth, then implement this improvement: Add more tests.");
    }
}
