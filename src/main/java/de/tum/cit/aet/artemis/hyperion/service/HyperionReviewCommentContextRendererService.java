package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Renders existing consistency-check review-thread context into deterministic JSON for Hyperion prompts.
 * Only threads that start with a consistency-check comment are included, and only the first comment is serialized.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionReviewCommentContextRendererService {

    private static final Comparator<Comment> CHRONOLOGICAL_COMMENT_ORDER = Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Comment::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<Comment> NEWEST_FIRST_COMMENT_ORDER = Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Comment::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewCommentContextRendererService.class);

    /** Maximum number of characters kept per serialized comment text in prompt context. */
    private static final int MAX_COMMENT_TEXT_LENGTH = 500;

    /** Maximum number of serialized comments included in prompt context. */
    private static final int MAX_SERIALIZED_COMMENTS = 100;

    /** Maximum number of selected feedback threads included in prompt context. */
    private static final int MAX_SELECTED_FEEDBACK_THREADS = 25;

    /** Suffix appended when serialized comment text is truncated. */
    private static final String TRUNCATED_SUFFIX = "... (truncated)";

    private static final class RemainingSerializedComments {

        private int remainingComments;

        private RemainingSerializedComments(int remainingComments) {
            this.remainingComments = remainingComments;
        }

        private boolean exhausted() {
            return remainingComments <= 0;
        }

        private int remainingComments() {
            return remainingComments;
        }

        private void consume(int consumedComments) {
            remainingComments -= consumedComments;
        }
    }

    private final CommentThreadRepository commentThreadRepository;

    private final ObjectMapper objectMapper;

    /**
     * Creates a renderer for deterministic review-thread prompt context serialization.
     *
     * @param commentThreadRepository repository used to load review threads with comments
     * @param objectMapper            mapper used to serialize prompt payloads as JSON
     */
    public HyperionReviewCommentContextRendererService(CommentThreadRepository commentThreadRepository, ObjectMapper objectMapper) {
        this.commentThreadRepository = commentThreadRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes consistency-check review threads into a stable JSON payload containing a {@code threads} array.
     * A thread is included only if its first comment is of type {@code CONSISTENCY_CHECK}.
     * Only that first comment is embedded in prompt context, with at most {@link #MAX_SERIALIZED_COMMENTS} comments total.
     * Each serialized comment text is truncated to {@link #MAX_COMMENT_TEXT_LENGTH} characters.
     * Threads are ordered by descending thread id (newest first) to prioritize recent findings while keeping deterministic output.
     *
     * @param exerciseId id of the exercise whose threads should be serialized
     * @return JSON string for prompt embedding
     */
    public String renderReviewThreads(long exerciseId) {
        Set<CommentThread> threads = commentThreadRepository.findWithCommentsAndGroupByExerciseId(exerciseId);
        if (threads.isEmpty()) {
            return "{\"threads\":[]}";
        }
        List<Map<String, Object>> serializedThreads = new ArrayList<>();
        List<CommentThread> sortedThreads = threads.stream().sorted(Comparator.comparing(CommentThread::getId, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        int serializedCommentCount = 0;
        for (CommentThread thread : sortedThreads) {
            if (serializedCommentCount >= MAX_SERIALIZED_COMMENTS) {
                break;
            }

            Set<Comment> comments = thread.getComments();
            if (comments == null || comments.isEmpty()) {
                continue;
            }
            Comment firstComment = comments.stream().min(Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Comment::getId, Comparator.nullsLast(Comparator.naturalOrder()))).orElse(null);
            if (firstComment == null) {
                continue;
            }
            if (firstComment.getType() != CommentType.CONSISTENCY_CHECK) {
                continue;
            }

            Map<String, Object> serializedThread = new LinkedHashMap<>();
            serializedThread.put("targetType", thread.getTargetType() != null ? thread.getTargetType().name() : null);
            serializedThread.put("filePath", thread.getFilePath() != null ? thread.getFilePath() : thread.getInitialFilePath());
            serializedThread.put("lineNumber", thread.getLineNumber() != null ? thread.getLineNumber() : thread.getInitialLineNumber());
            serializedThread.put("resolved", thread.isResolved());
            serializedThread.put("outdated", thread.isOutdated());
            CommentThreadGroup group = thread.getGroup();
            serializedThread.put("groupId", group != null ? group.getId() : null);

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
     * Serializes explicitly selected review threads into a stable JSON payload for Hyperion code generation prompts.
     * Only active threads that belong to the selected repository type are included.
     *
     * @param exerciseId     the exercise id
     * @param repositoryType the repository currently being generated
     * @param threadIds      the explicitly selected review-thread ids
     * @return JSON payload containing the selected feedback threads
     */
    public String renderCodeGenerationSelectedFeedback(long exerciseId, RepositoryType repositoryType, Collection<Long> threadIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repositoryType", repositoryType != null ? repositoryType.name() : null);

        if (repositoryType == null || threadIds == null || threadIds.isEmpty()) {
            payload.put("threads", List.of());
            return serializePayload(payload, exerciseId);
        }

        CommentThreadLocationType targetType = mapRepositoryTypeToThreadLocationType(repositoryType);
        if (targetType == null) {
            payload.put("threads", List.of());
            return serializePayload(payload, exerciseId);
        }

        List<Long> orderedThreadIds = threadIds.stream().filter(Objects::nonNull).distinct().limit(MAX_SELECTED_FEEDBACK_THREADS).toList();
        if (orderedThreadIds.isEmpty()) {
            payload.put("threads", List.of());
            return serializePayload(payload, exerciseId);
        }

        Map<Long, Integer> threadOrder = new LinkedHashMap<>();
        for (int index = 0; index < orderedThreadIds.size(); index++) {
            threadOrder.put(orderedThreadIds.get(index), index);
        }

        RemainingSerializedComments remainingSerializedComments = new RemainingSerializedComments(MAX_SERIALIZED_COMMENTS);
        List<Map<String, Object>> serializedThreads = new ArrayList<>();
        List<CommentThread> selectedThreads = commentThreadRepository.findWithCommentsByExerciseIdAndIdIn(exerciseId, orderedThreadIds).stream()
                .filter(thread -> thread.getTargetType() == targetType && !thread.isResolved() && !thread.isOutdated())
                .sorted(Comparator.comparing(thread -> threadOrder.getOrDefault(thread.getId(), Integer.MAX_VALUE))).toList();
        for (CommentThread thread : selectedThreads) {
            if (remainingSerializedComments.exhausted()) {
                break;
            }
            Map<String, Object> serializedThread = serializeSelectedFeedbackThread(thread, remainingSerializedComments);
            if (serializedThread != null) {
                serializedThreads.add(serializedThread);
            }
        }

        payload.put("threads", serializedThreads);
        return serializePayload(payload, exerciseId);
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
            String severity = consistencyContent.severity() != null ? consistencyContent.severity().name() : "UNKNOWN";
            String category = consistencyContent.category() != null ? consistencyContent.category().name() : "UNKNOWN";
            String prefix = "[" + severity + "/" + category + "] ";
            return truncateText(prefix + sanitizeAndNormalizeText(consistencyContent.text()));
        }
        return truncateText(sanitizeAndNormalizeText(content.toString()));
    }

    /**
     * Serializes one explicitly selected feedback thread while consuming from the shared global comment budget.
     *
     * @param thread                      the selected review thread
     * @param remainingSerializedComments mutable remaining comment limit shared across all selected threads
     * @return serialized thread payload, or {@code null} when the thread contributes no serializable comments
     */
    private Map<String, Object> serializeSelectedFeedbackThread(CommentThread thread, RemainingSerializedComments remainingSerializedComments) {
        if (!hasSerializableComments(thread, remainingSerializedComments)) {
            return null;
        }

        List<Map<String, Object>> serializedComments = serializeThreadComments(thread, remainingSerializedComments.remainingComments());
        if (serializedComments.isEmpty()) {
            return null;
        }
        remainingSerializedComments.consume(serializedComments.size());

        return createSerializedThreadPayload(thread, serializedComments);
    }

    private boolean hasSerializableComments(CommentThread thread, RemainingSerializedComments remainingSerializedComments) {
        return thread != null && remainingSerializedComments != null && !remainingSerializedComments.exhausted() && thread.getComments() != null && !thread.getComments().isEmpty();
    }

    private List<Map<String, Object>> serializeThreadComments(CommentThread thread, int remainingCommentBudget) {
        return thread.getComments().stream().sorted(NEWEST_FIRST_COMMENT_ORDER).limit(remainingCommentBudget).sorted(CHRONOLOGICAL_COMMENT_ORDER)
                .map(this::createSerializedCommentPayload).toList();
    }

    private Map<String, Object> createSerializedCommentPayload(Comment comment) {
        Map<String, Object> serializedComment = new LinkedHashMap<>();
        serializedComment.put("type", comment.getType() != null ? comment.getType().name() : null);
        serializedComment.put("text", extractCommentText(comment.getContent()));
        return serializedComment;
    }

    private Map<String, Object> createSerializedThreadPayload(CommentThread thread, List<Map<String, Object>> serializedComments) {
        Map<String, Object> serializedThread = new LinkedHashMap<>();
        serializedThread.put("id", thread.getId());
        serializedThread.put("targetType", thread.getTargetType() != null ? thread.getTargetType().name() : null);
        serializedThread.put("filePath", thread.getFilePath() != null ? thread.getFilePath() : thread.getInitialFilePath());
        serializedThread.put("lineNumber", thread.getLineNumber() != null ? thread.getLineNumber() : thread.getInitialLineNumber());
        serializedThread.put("comments", serializedComments);
        return serializedThread;
    }

    /**
     * Maps a code-generation repository type to the corresponding review-thread location type.
     *
     * @param repositoryType the repository currently being generated
     * @return matching thread location type, or {@code null} when the repository type has no review-thread mapping
     */
    private CommentThreadLocationType mapRepositoryTypeToThreadLocationType(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> CommentThreadLocationType.TEMPLATE_REPO;
            case SOLUTION -> CommentThreadLocationType.SOLUTION_REPO;
            case TESTS -> CommentThreadLocationType.TEST_REPO;
            default -> null;
        };
    }

    /**
     * Serializes a review-thread payload and falls back to an empty-thread JSON object on serialization failure.
     *
     * @param payload    the payload to serialize
     * @param exerciseId the exercise id used for diagnostic logging
     * @return serialized JSON payload, or {@code "{\"threads\":[]}" } when serialization fails
     */
    private String serializePayload(Map<String, Object> payload, long exerciseId) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e) {
            log.warn("Failed to serialize review-thread prompt context for exercise {}", exerciseId, e);
            return "{\"threads\":[]}";
        }
    }

    /**
     * Sanitizes and normalizes text for safe and compact prompt embedding.
     *
     * @param text raw text
     * @return sanitized, normalized, single-line-safe text
     */
    private String sanitizeAndNormalizeText(String text) {
        return normalizeWhitespace(HyperionUtils.sanitizeInput(text));
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
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= MAX_COMMENT_TEXT_LENGTH) {
            return text;
        }
        int maxPrefixLength = MAX_COMMENT_TEXT_LENGTH - TRUNCATED_SUFFIX.length();
        return text.substring(0, maxPrefixLength) + TRUNCATED_SUFFIX;
    }
}
