package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Renders existing consistency-check review-thread context into deterministic JSON for Hyperion prompts.
 * Only threads that start with a consistency-check comment are included, and only the first comment is serialized.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionReviewCommentContextRendererService {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewCommentContextRendererService.class);

    /** Pattern matching control characters except newline (\n), carriage return (\r), and tab (\t). */
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    /**
     * Pattern matching prompt template delimiter lines (e.g. "--- BEGIN USER REQUIREMENTS ---").
     * Stripping these prevents injecting fake section boundaries into prompt context.
     */
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("^\\s*-{3,}\\s*(BEGIN|END)\\s+.*-{3,}$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /**
     * Pattern matching template variable sequences (e.g. "{{variable}}").
     * Stripping these prevents injecting fake template placeholders.
     */
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{[^}]*\\}\\}");

    /** Maximum number of characters kept per serialized comment text in prompt context. */
    private static final int MAX_COMMENT_TEXT_LENGTH = 500;

    /** Maximum number of serialized comments included in prompt context. */
    private static final int MAX_SERIALIZED_COMMENTS = 100;

    /** Suffix appended when serialized comment text is truncated. */
    private static final String TRUNCATED_SUFFIX = "... (truncated)";

    private final CommentThreadRepository commentThreadRepository;

    private final ObjectMapper objectMapper;

    public HyperionReviewCommentContextRendererService(CommentThreadRepository commentThreadRepository, ObjectMapper objectMapper) {
        this.commentThreadRepository = commentThreadRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes consistency-check review threads into a stable JSON payload containing a {@code threads} array.
     * A thread is included only if its first comment is of type {@code CONSISTENCY_CHECK}.
     * Only that first comment is embedded in prompt context, with at most {@link #MAX_SERIALIZED_COMMENTS} comments total.
     * Each serialized comment text is truncated to {@link #MAX_COMMENT_TEXT_LENGTH} characters.
     *
     * @param exerciseId id of the exercise whose threads should be serialized
     * @return JSON string for prompt embedding
     */
    public String renderReviewThreads(long exerciseId) {
        Set<CommentThread> threads = commentThreadRepository.findWithCommentsByExerciseId(exerciseId);
        if (threads == null || threads.isEmpty()) {
            return "{\"threads\":[]}";
        }
        List<Map<String, Object>> serializedThreads = new ArrayList<>();
        List<CommentThread> sortedThreads = threads.stream().sorted(Comparator.comparing(CommentThread::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        int serializedCommentCount = 0;
        for (CommentThread thread : sortedThreads) {
            if (serializedCommentCount >= MAX_SERIALIZED_COMMENTS) {
                break;
            }

            Map<String, Object> serializedThread = new LinkedHashMap<>();
            serializedThread.put("targetType", thread.getTargetType() != null ? thread.getTargetType().name() : null);
            serializedThread.put("filePath", thread.getFilePath() != null ? thread.getFilePath() : thread.getInitialFilePath());
            serializedThread.put("lineNumber", thread.getLineNumber() != null ? thread.getLineNumber() : thread.getInitialLineNumber());
            serializedThread.put("resolved", thread.isResolved());
            serializedThread.put("outdated", thread.isOutdated());
            CommentThreadGroup group = thread.getGroup();
            serializedThread.put("groupId", group != null ? group.getId() : null);

            List<Comment> sortedComments = thread.getComments() == null ? List.of()
                    : thread.getComments().stream().sorted(Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Comment::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            if (sortedComments.isEmpty()) {
                continue;
            }
            Comment firstComment = sortedComments.get(0);
            if (firstComment.getType() != CommentType.CONSISTENCY_CHECK) {
                continue;
            }

            List<Map<String, Object>> serializedComments = new ArrayList<>(1);
            Map<String, Object> serializedComment = new LinkedHashMap<>();
            serializedComment.put("type", firstComment.getType().name());
            serializedComment.put("text", extractCommentText(firstComment.getContent()));
            serializedComments.add(serializedComment);
            serializedCommentCount++;
            serializedThread.put("comments", serializedComments);
            serializedThreads.add(serializedThread);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("threads", serializedThreads);
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e) {
            log.warn("Failed to serialize existing review threads for exercise {}", exerciseId, e);
            return "{\"threads\":[]}";
        }
    }

    /**
     * Extracts the prompt-relevant text from a polymorphic review comment content DTO.
     *
     * @param content review comment content
     * @return normalized text representation used in prompt context
     */
    private String extractCommentText(CommentContentDTO content) {
        if (content == null) {
            return "";
        }
        if (content instanceof UserCommentContentDTO userContent) {
            return truncateText(sanitizeAndNormalizeText(userContent.text()));
        }
        if (content instanceof ConsistencyIssueCommentContentDTO consistencyContent) {
            String prefix = "[" + consistencyContent.severity().name() + "/" + consistencyContent.category().name() + "] ";
            return truncateText(prefix + sanitizeAndNormalizeText(consistencyContent.text()));
        }
        return truncateText(sanitizeAndNormalizeText(content.toString()));
    }

    /**
     * Sanitizes and normalizes text for safe and compact prompt embedding.
     *
     * @param text raw text
     * @return sanitized, normalized, single-line-safe text
     */
    private String sanitizeAndNormalizeText(String text) {
        return normalizeWhitespace(sanitizeInput(text));
    }

    /**
     * Sanitizes prompt input by removing disallowed control characters, delimiter lines, and template placeholders.
     *
     * @param input raw input text
     * @return sanitized text (never {@code null})
     */
    private String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");
        sanitized = DELIMITER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = TEMPLATE_VAR_PATTERN.matcher(sanitized).replaceAll("");
        return sanitized.trim();
    }

    /**
     * Normalizes line breaks and trims surrounding whitespace for compact single-line prompt embedding.
     *
     * @param text raw text
     * @return normalized text with escaped newlines
     */
    private String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n").trim();
    }

    /**
     * Truncates serialized comment text to a bounded size for prompt stability.
     *
     * @param text normalized text
     * @return text limited to {@link #MAX_COMMENT_TEXT_LENGTH} characters
     */
    private String truncateText(String text) {
        if (text.length() <= MAX_COMMENT_TEXT_LENGTH) {
            return text;
        }
        int maxPrefixLength = MAX_COMMENT_TEXT_LENGTH - TRUNCATED_SUFFIX.length();
        return text.substring(0, maxPrefixLength) + TRUNCATED_SUFFIX;
    }
}
