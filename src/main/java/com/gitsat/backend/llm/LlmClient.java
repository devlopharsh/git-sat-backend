package com.gitsat.backend.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitsat.backend.dto.SummaryResponse.FileSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
    private static final int MAX_SUMMARY_CHARS = 160;
    private static final int MAX_PATCH_CHARS = 6000;
    private static final int MAX_MESSAGES = 20;
    private static final int MAX_PATCHES = 6;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBase;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final double topP;
    private final int maxTokens;
    private final int maxRetries;
    private final long retryBaseMs;
    private final long retryMaxMs;

    public LlmClient(
            ObjectMapper objectMapper,
            @Value("${NVIDIA_API_BASE:https://integrate.api.nvidia.com/v1}") String apiBase,
            @Value("${NVIDIA_API_KEY:}") String apiKey,
            @Value("${NVIDIA_MODEL:openai/gpt-oss-20b}") String model,
            @Value("${NVIDIA_TEMPERATURE:1}") double temperature,
            @Value("${NVIDIA_TOP_P:1}") double topP,
            @Value("${NVIDIA_MAX_TOKENS:4096}") int maxTokens,
            @Value("${NVIDIA_RETRY_MAX:3}") int maxRetries,
            @Value("${NVIDIA_RETRY_BASE_MS:500}") long retryBaseMs,
            @Value("${NVIDIA_RETRY_MAX_MS:4000}") long retryMaxMs
    ) {
        this.objectMapper = objectMapper;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBaseMs = Math.max(100, retryBaseMs);
        this.retryMaxMs = Math.max(500, retryMaxMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String summarizeFile(
            String path,
            int insertions,
            int deletions,
            List<String> commitMessages,
            List<String> patches
    ) {
        try {
            ensureConfigured();
            String prompt = buildFilePrompt(path, insertions, deletions, commitMessages, patches);
            String content = callLlm(prompt);
            return normalizeSummary(content);
        } catch (RuntimeException ex) {
            logger.warn("LLM file summary failed, using fallback. path={}", path, ex);
            return "Minor edits or formatting changes.";
        }
    }

    public String summarizeOverall(List<FileSummaryDto> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        try {
            ensureConfigured();
            String prompt = buildOverallPrompt(files);
            String content = callLlm(prompt);
            return normalizeOverall(content);
        } catch (RuntimeException ex) {
            logger.warn("LLM overall summary failed, returning empty overall.", ex);
            return "";
        }
    }

    public String summarizeDetailedOverall(List<FileSummaryDto> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        try {
            ensureConfigured();
            String prompt = buildDetailedOverallPrompt(files);
            String content = callLlm(prompt);
            return normalizeDetailedOverall(content);
        } catch (RuntimeException ex) {
            logger.warn("LLM detailed overall summary failed, returning fallback detail.", ex);
            return fallbackDetailedOverall(files);
        }
    }

    public String summarizeGoal(com.gitsat.backend.dto.SummaryRequest request, List<FileSummaryDto> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        try {
            ensureConfigured();
            String prompt = buildGoalPrompt(request, files);
            String content = callLlm(prompt);
            return normalizeGoal(content);
        } catch (RuntimeException ex) {
            logger.warn("LLM goal summary failed, returning fallback goal.", ex);
            return fallbackGoal(request, files);
        }
    }

    public String summarizeSuggestion(
            com.gitsat.backend.dto.SummaryRequest request,
            List<FileSummaryDto> files,
            String goal
    ) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        try {
            ensureConfigured();
            String prompt = buildSuggestionPromptText(request, files, goal);
            String content = callLlm(prompt);
            return normalizeSuggestion(content);
        } catch (RuntimeException ex) {
            logger.warn("LLM suggestion summary failed, returning fallback suggestion.", ex);
            return fallbackSuggestion(files, goal);
        }
    }

    public String buildSuggestionPrompt(
            com.gitsat.backend.dto.SummaryRequest request,
            List<FileSummaryDto> files,
            String goal,
            String suggestion
    ) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        try {
            ensureConfigured();
            String prompt = buildSuggestionPromptPrompt(request, files, goal, suggestion);
            String content = callLlm(prompt);
            return normalizeSuggestionPrompt(content);
        } catch (RuntimeException ex) {
            logger.warn("LLM suggestion prompt failed, returning fallback prompt.", ex);
            return fallbackSuggestionPrompt(goal, suggestion);
        }
    }

    private String buildFilePrompt(
            String path,
            int insertions,
            int deletions,
            List<String> commitMessages,
            List<String> patches
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are summarizing a single file change for a git commit summary.\n");
        sb.append("Constraints:\n");
        sb.append("- Return ONE sentence, max 160 characters.\n");
        sb.append("- Use commit messages and patch content to infer intent.\n");
        sb.append("- If the change is minor, return exactly: Minor edits or formatting changes.\n");
        sb.append("- Do not include quotes or markdown.\n\n");
        sb.append("File: ").append(path).append('\n');
        sb.append("Insertions: ").append(insertions).append('\n');
        sb.append("Deletions: ").append(deletions).append('\n');
        sb.append("Commit messages:\n");
        sb.append(joinLimited(commitMessages, MAX_MESSAGES, 200)).append('\n');
        sb.append("Patches:\n");
        sb.append(joinLimited(patches, MAX_PATCHES, MAX_PATCH_CHARS)).append('\n');
        sb.append("Summary:");
        return sb.toString();
    }

    private String buildOverallPrompt(List<FileSummaryDto> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are summarizing a set of file changes.\n");
        sb.append("Constraints:\n");
        sb.append("- Return ONE sentence.\n");
        sb.append("- Be concise and concrete; no placeholders.\n");
        sb.append("- Do not include quotes or markdown.\n\n");
        sb.append("File summaries:\n");
        int count = 0;
        for (FileSummaryDto file : files) {
            if (file == null) {
                continue;
            }
            count++;
            sb.append("- ").append(file.path()).append(": ").append(file.summary()).append('\n');
            if (count >= 30) {
                break;
            }
        }
        sb.append("Overall summary:");
        return sb.toString();
    }

    private String buildDetailedOverallPrompt(List<FileSummaryDto> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are writing a detailed software change summary for a CLI report.\n");
        sb.append("Constraints:\n");
        sb.append("- Return plain text only; no markdown, bullets, or quotes.\n");
        sb.append("- Write 3 to 6 sentences.\n");
        sb.append("- Mention the most important file changes concretely.\n");
        sb.append("- Cover all meaningful changes represented below.\n");
        sb.append("- Keep the response under 1200 characters.\n\n");
        sb.append("Changed files:\n");
        int count = 0;
        for (FileSummaryDto file : files) {
            if (file == null) {
                continue;
            }
            count++;
            sb.append("- ")
                    .append(file.path())
                    .append(" (")
                    .append(file.insertions())
                    .append(" insertions, ")
                    .append(file.deletions())
                    .append(" deletions): ")
                    .append(file.summary())
                    .append('\n');
            if (count >= 50) {
                break;
            }
        }
        sb.append("Detailed overall summary:");
        return sb.toString();
    }

    private String buildGoalPrompt(com.gitsat.backend.dto.SummaryRequest request, List<FileSummaryDto> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are inferring the main user goal behind a set of software changes.\n");
        sb.append("Constraints:\n");
        sb.append("- Return ONE sentence.\n");
        sb.append("- Describe what the user is trying to achieve with these changes.\n");
        sb.append("- Be concrete and outcome-focused.\n");
        sb.append("- Do not include quotes or markdown.\n\n");
        appendRequestContext(sb, request);
        appendFileSummaries(sb, files, 30);
        sb.append("Goal:");
        return sb.toString();
    }

    private String buildSuggestionPromptText(
            com.gitsat.backend.dto.SummaryRequest request,
            List<FileSummaryDto> files,
            String goal
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are suggesting one practical improvement for a software change set.\n");
        sb.append("Constraints:\n");
        sb.append("- Return ONE sentence.\n");
        sb.append("- The suggestion must help enhance the stated goal.\n");
        sb.append("- Keep it specific, actionable, and technically relevant.\n");
        sb.append("- Do not include quotes or markdown.\n\n");
        appendRequestContext(sb, request);
        appendFileSummaries(sb, files, 30);
        sb.append("Goal: ").append(goal == null ? "" : goal).append('\n');
        sb.append("Suggestion:");
        return sb.toString();
    }

    private String buildSuggestionPromptPrompt(
            com.gitsat.backend.dto.SummaryRequest request,
            List<FileSummaryDto> files,
            String goal,
            String suggestion
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are writing a single prompt that could be given to an AI assistant.\n");
        sb.append("Constraints:\n");
        sb.append("- Return ONE sentence only.\n");
        sb.append("- Write it as an imperative prompt requesting work.\n");
        sb.append("- The prompt must implement or explore the suggestion in a practical way.\n");
        sb.append("- Mention the goal context briefly.\n");
        sb.append("- Do not include quotes or markdown.\n\n");
        appendRequestContext(sb, request);
        appendFileSummaries(sb, files, 20);
        sb.append("Goal: ").append(goal == null ? "" : goal).append('\n');
        sb.append("Suggestion: ").append(suggestion == null ? "" : suggestion).append('\n');
        sb.append("Suggestion prompt:");
        return sb.toString();
    }

    private String callLlm(String prompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "You are a concise software change summarizer."),
                Map.of("role", "user", "content", prompt)
        ));
        payload.put("temperature", temperature);
        payload.put("top_p", topP);
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize LLM request.", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return sendWithRetry(request);
    }

    private String sendWithRetry(HttpRequest request) {
        int attempt = 0;
        while (true) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return extractContent(response.body());
                }
                if (!isRetryableStatus(status) || attempt >= maxRetries) {
                    throw new IllegalStateException("LLM request failed with status " + status);
                }
                backoff(attempt, status);
            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    throw new IllegalStateException("LLM request failed.", e);
                }
                backoff(attempt, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("LLM request interrupted.", e);
            }
            attempt++;
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || (status >= 500 && status <= 599);
    }

    private void backoff(int attempt, int status) {
        long base = retryBaseMs * (1L << Math.min(attempt, 6));
        long capped = Math.min(base, retryMaxMs);
        long jitter = ThreadLocalRandom.current().nextLong(0, 150);
        long sleepMs = Math.min(capped + jitter, retryMaxMs);
        logger.warn("LLM request retrying after {} ms (status={})", sleepMs, status);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM retry interrupted.", e);
        }
    }

    private String extractContent(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode contentNode = choices.get(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                return "";
            }
            if (contentNode.isTextual()) {
                return contentNode.asText();
            }
            if (contentNode.isArray()) {
                StringBuilder content = new StringBuilder();
                for (JsonNode item : contentNode) {
                    JsonNode textNode = item.path("text");
                    if (textNode.isTextual()) {
                        content.append(textNode.asText());
                    }
                }
                return content.toString();
            }
            return contentNode.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse LLM response.", e);
        }
    }

    private String normalizeSummary(String content) {
        String cleaned = normalizeLine(content);
        if (cleaned.isBlank()) {
            return "Minor edits or formatting changes.";
        }
        if (cleaned.length() > MAX_SUMMARY_CHARS) {
            return cleaned.substring(0, MAX_SUMMARY_CHARS - 3).trim() + "...";
        }
        return cleaned;
    }

    private String normalizeOverall(String content) {
        String cleaned = normalizeLine(content);
        return cleaned;
    }

    private String normalizeDetailedOverall(String content) {
        String cleaned = normalizeLine(content);
        if (cleaned.isBlank()) {
            return "";
        }
        if (cleaned.length() > 1200) {
            return cleaned.substring(0, 1197).trim() + "...";
        }
        return cleaned;
    }

    private String normalizeGoal(String content) {
        return normalizeBoundedLine(content, 240);
    }

    private String normalizeSuggestion(String content) {
        return normalizeBoundedLine(content, 260);
    }

    private String normalizeSuggestionPrompt(String content) {
        return normalizeBoundedLine(content, 320);
    }

    private String normalizeLine(String content) {
        if (content == null) {
            return "";
        }
        String cleaned = content.replace('\n', ' ').replace('\r', ' ').trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private String normalizeBoundedLine(String content, int maxChars) {
        String cleaned = normalizeLine(content);
        if (cleaned.isBlank()) {
            return "";
        }
        if (cleaned.length() > maxChars) {
            return cleaned.substring(0, maxChars - 3).trim() + "...";
        }
        return cleaned;
    }

    private String fallbackDetailedOverall(List<FileSummaryDto> files) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (FileSummaryDto file : files) {
            if (file == null || file.summary() == null || file.summary().isBlank()) {
                continue;
            }
            if (count > 0) {
                sb.append(' ');
            }
            sb.append(file.path())
                    .append(": ")
                    .append(file.summary());
            count++;
            if (count >= 8 || sb.length() >= 1000) {
                break;
            }
        }
        return sb.toString();
    }

    private String fallbackGoal(com.gitsat.backend.dto.SummaryRequest request, List<FileSummaryDto> files) {
        if (request != null && request.repo() != null && !request.repo().isBlank()) {
            return "Improve " + request.repo() + " by delivering the functionality reflected in the recent code changes.";
        }
        if (!files.isEmpty() && files.get(0) != null && files.get(0).summary() != null && !files.get(0).summary().isBlank()) {
            return "Implement the main capability reflected by the recent file changes and summaries.";
        }
        return "Implement the intended software improvement reflected by the recent code changes.";
    }

    private String fallbackSuggestion(List<FileSummaryDto> files, String goal) {
        if (goal != null && !goal.isBlank()) {
            return "Add validation, edge-case handling, and automated tests to make this goal more reliable in real usage.";
        }
        return "Add validation and tests around the changed areas to strengthen the outcome of these changes.";
    }

    private String fallbackSuggestionPrompt(String goal, String suggestion) {
        StringBuilder sb = new StringBuilder("Review the recent changes");
        if (goal != null && !goal.isBlank()) {
            sb.append(" with the goal of ").append(goal);
        }
        if (suggestion != null && !suggestion.isBlank()) {
            sb.append(", then implement this improvement: ").append(suggestion);
        }
        sb.append('.');
        return normalizeBoundedLine(sb.toString(), 320);
    }

    private void appendRequestContext(StringBuilder sb, com.gitsat.backend.dto.SummaryRequest request) {
        if (request == null) {
            return;
        }
        if (request.repo() != null && !request.repo().isBlank()) {
            sb.append("Repository: ").append(request.repo()).append('\n');
        }
        if (request.author() != null && !request.author().isBlank()) {
            sb.append("Author: ").append(request.author()).append('\n');
        }
        if (request.since() != null && !request.since().isBlank()) {
            sb.append("Since: ").append(request.since()).append('\n');
        }
    }

    private void appendFileSummaries(StringBuilder sb, List<FileSummaryDto> files, int limit) {
        sb.append("File summaries:\n");
        int count = 0;
        for (FileSummaryDto file : files) {
            if (file == null) {
                continue;
            }
            count++;
            sb.append("- ").append(file.path()).append(": ").append(file.summary()).append('\n');
            if (count >= limit) {
                break;
            }
        }
    }

    private String joinLimited(List<String> items, int maxItems, int maxChars) {
        if (items == null || items.isEmpty()) {
            return "(none)";
        }
        StringJoiner joiner = new StringJoiner("\n");
        int count = 0;
        int totalChars = 0;
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (count >= maxItems) {
                break;
            }
            String trimmed = item.trim();
            if (totalChars + trimmed.length() > maxChars) {
                int remaining = Math.max(0, maxChars - totalChars);
                if (remaining > 0) {
                    joiner.add(trimmed.substring(0, remaining));
                }
                break;
            }
            joiner.add(trimmed);
            totalChars += trimmed.length();
            count++;
        }
        String result = joiner.toString();
        return result.isBlank() ? "(none)" : result;
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NVIDIA_API_KEY is not configured.");
        }
        if (apiBase == null || apiBase.isBlank()) {
            throw new IllegalStateException("NVIDIA_API_BASE is not configured.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("NVIDIA_MODEL is not configured.");
        }
    }

    private String buildUrl() {
        String base = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        return base + "/chat/completions";
    }
}
