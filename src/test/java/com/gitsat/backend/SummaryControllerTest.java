package com.gitsat.backend;

import com.gitsat.backend.controller.SummaryController;
import com.gitsat.backend.dto.SummaryRequest;
import com.gitsat.backend.dto.SummaryResponse;
import com.gitsat.backend.llm.LlmClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryControllerTest {

    @Mock
    private LlmClient llmClient;

    @Test
    void getSummaryAggregatesFilesAcrossCommitsAndBuildsFinalResponse() {
        SummaryController controller = new SummaryController(llmClient);
        SummaryRequest request = new SummaryRequest(
                "git-sat-backend",
                "2026-05-01",
                "harsh",
                List.of(
                        new SummaryRequest.CommitDto(
                                "c1",
                                "Add auth flow",
                                "2026-05-10T10:00:00Z",
                                List.of(
                                        new SummaryRequest.FileChangeDto("src/Auth.java", 10, 2, "@@ auth patch 1"),
                                        new SummaryRequest.FileChangeDto("src/Util.java", 3, 1, "@@ util patch"),
                                        new SummaryRequest.FileChangeDto(" ", 99, 99, "@@ ignored")
                                )
                        ),
                        new SummaryRequest.CommitDto(
                                "c2",
                                "Polish auth flow",
                                "2026-05-11T10:00:00Z",
                                List.of(
                                        new SummaryRequest.FileChangeDto("src/Auth.java", 4, 1, "@@ auth patch 2")
                                )
                        )
                )
        );

        when(llmClient.summarizeFile(anyString(), anyInt(), anyInt(), anyList(), anyList()))
                .thenAnswer(invocation -> "summary for " + invocation.getArgument(0, String.class));
        when(llmClient.summarizeOverall(anyList())).thenReturn("overall summary");
        when(llmClient.summarizeDetailedOverall(anyList())).thenReturn("detailed summary");
        when(llmClient.summarizeGoal(any(SummaryRequest.class), anyList())).thenReturn("goal summary");
        when(llmClient.summarizeSuggestion(any(SummaryRequest.class), anyList(), anyString()))
                .thenReturn("suggestion summary");
        when(llmClient.buildSuggestionPrompt(any(SummaryRequest.class), anyList(), anyString(), anyString()))
                .thenReturn("prompt summary");

        SummaryResponse response = controller.getSummary(request);

        assertThat(response.overall()).isEqualTo("overall summary");
        assertThat(response.detailedOverall()).isEqualTo("detailed summary");
        assertThat(response.goal()).isEqualTo("goal summary");
        assertThat(response.suggestion()).isEqualTo("suggestion summary");
        assertThat(response.suggestionPrompt()).isEqualTo("prompt summary");
        assertThat(response.files()).hasSize(2);

        SummaryResponse.FileSummaryDto authFile = response.files().get(0);
        assertThat(authFile.path()).isEqualTo("src/Auth.java");
        assertThat(authFile.insertions()).isEqualTo(14);
        assertThat(authFile.deletions()).isEqualTo(3);
        assertThat(authFile.summary()).isEqualTo("summary for src/Auth.java");

        SummaryResponse.FileSummaryDto utilFile = response.files().get(1);
        assertThat(utilFile.path()).isEqualTo("src/Util.java");
        assertThat(utilFile.insertions()).isEqualTo(3);
        assertThat(utilFile.deletions()).isEqualTo(1);

        ArgumentCaptor<List<String>> commitMessagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> patchesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(1)).summarizeFile(
                org.mockito.ArgumentMatchers.eq("src/Auth.java"),
                org.mockito.ArgumentMatchers.eq(14),
                org.mockito.ArgumentMatchers.eq(3),
                commitMessagesCaptor.capture(),
                patchesCaptor.capture()
        );
        assertThat(commitMessagesCaptor.getValue()).containsExactly("Add auth flow", "Polish auth flow");
        assertThat(patchesCaptor.getValue()).containsExactly("@@ auth patch 1", "@@ auth patch 2");
    }
}
