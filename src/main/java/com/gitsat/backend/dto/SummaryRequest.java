package com.gitsat.backend.dto;

import java.util.List;

public record SummaryRequest(
        String repo,
        String since,
        String author,
        List<CommitDto> commits
) {
    public record CommitDto(
            String hash,
            String message,
            String date,
            List<FileChangeDto> files
    ) {}

    public record FileChangeDto(
            String path,
            int insertions,
            int deletions,
            String patch
    ) {}
}
