package com.gitsat.backend.dto;

import java.util.List;

public record SummaryResponse(
        List<FileSummaryDto> files,
        String overall,
        String detailedOverall,
        String goal,
        String suggestion,
        String suggestionPrompt
) {
    public record FileSummaryDto(
            String path,
            int insertions,
            int deletions,
            String summary
    ) {}
}
