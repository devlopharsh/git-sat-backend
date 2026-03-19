package com.gitsat.backend.controller;

import com.gitsat.backend.dto.SummaryRequest;
import com.gitsat.backend.dto.SummaryResponse;
import com.gitsat.backend.dto.SummaryResponse.FileSummaryDto;
import com.gitsat.backend.llm.LlmClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SummaryController {

    private final LlmClient llmClient;

    public SummaryController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostMapping("/summary")
    public SummaryResponse getSummary(@RequestBody SummaryRequest request) {
        Map<String, FileAggregate> aggregates = new LinkedHashMap<>();
        if (request.commits() != null) {
            for (SummaryRequest.CommitDto commit : request.commits()) {
                if (commit.files() == null) {
                    continue;
                }
                for (SummaryRequest.FileChangeDto fileChange : commit.files()) {
                    String path = fileChange.path();
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    FileAggregate agg = aggregates.computeIfAbsent(path, p -> new FileAggregate());
                    agg.insertions += fileChange.insertions();
                    agg.deletions += fileChange.deletions();
                    if (commit.message() != null && !commit.message().isBlank()) {
                        agg.commitMessages.add(commit.message());
                    }
                    if (fileChange.patch() != null && !fileChange.patch().isBlank()) {
                        agg.patches.add(fileChange.patch());
                    }
                }
            }
        }

        List<FileSummaryDto> files = new ArrayList<>();
        for (Map.Entry<String, FileAggregate> entry : aggregates.entrySet()) {
            String path = entry.getKey();
            FileAggregate agg = entry.getValue();
            String summary = llmClient.summarizeFile(path, agg.insertions, agg.deletions, agg.commitMessages, agg.patches);
            files.add(new FileSummaryDto(path, agg.insertions, agg.deletions, summary));
        }

        String overall = llmClient.summarizeOverall(files);
        String detailedOverall = llmClient.summarizeDetailedOverall(files);

        return new SummaryResponse(files, overall, detailedOverall);
    }

    private static class FileAggregate {
        int insertions = 0;
        int deletions = 0;
        List<String> commitMessages = new ArrayList<>();
        List<String> patches = new ArrayList<>();
    }
}
