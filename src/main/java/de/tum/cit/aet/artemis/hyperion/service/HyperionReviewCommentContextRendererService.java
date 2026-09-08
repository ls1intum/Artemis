package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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
 * Renders review threads into JSON for Hyperion prompts.
 * <p>
 * The output is deterministic for a given database state: every ordering is total and every limit is fixed, so the same exercise renders the same context twice and a prompt change
 * is the only thing that can move a result.
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

    private static final int MAX_COMMENT_TEXT_LENGTH = 500;

    private static final int MAX_SERIALIZED_COMMENTS = 100;

    private static final int MAX_SELECTED_FEEDBACK_THREADS = 25;

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

    public HyperionReviewCommentContextRendererService(CommentThreadRepository commentThreadRepository, ObjectMapper objectMapper) {
        this.commentThreadRepository = commentThreadRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the exercise's earlier consistency-check findings so a new check can avoid repeating them.
     * <p>
     * A thread counts as a finding only when its <em>first</em> comment is a consistency-check comment; the instructor's replies to it are the discussion, not the finding, and are
     * left out. Newest threads are serialized first so that the budget, when it runs out, drops the oldest findings.
     *
     * @param exerciseId the exercise whose threads to serialize
     * @return a JSON object with a {@code threads} array, empty when there is nothing to report
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
            Comment firstComment = comments.stream().min(CHRONOLOGICAL_COMMENT_ORDER).orElse(null);
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
     * Serializes the instructor-selected feedback that applies to one repository, dropping threads that point at a different repository or have already been resolved.
     *
     * @param exerciseId     the exercise the threads belong to
     * @param repositoryType the repository currently being generated
     * @param threadIds      the thread ids the instructor selected
     * @return a JSON object naming the repository and its selected threads
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

        payload.put("threads", serializeSelectedThreads(exerciseId, threadIds, thread -> thread.getTargetType() == targetType && !thread.isResolved() && !thread.isOutdated()));
        return serializePayload(payload, exerciseId);
    }

    /**
     * Serializes the selected threads the {@code activeFilter} admits, in the order the instructor selected them, until the comment budget runs out. Selection order is preserved
     * through the database round-trip, which returns them in its own order, so the instructor's priority survives the budget cut-off.
     */
    private List<Map<String, Object>> serializeSelectedThreads(long exerciseId, Collection<Long> threadIds, Predicate<CommentThread> activeFilter) {
        List<Long> orderedThreadIds = threadIds.stream().filter(Objects::nonNull).distinct().limit(MAX_SELECTED_FEEDBACK_THREADS).toList();
        if (orderedThreadIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> threadOrder = new LinkedHashMap<>();
        for (int index = 0; index < orderedThreadIds.size(); index++) {
            threadOrder.put(orderedThreadIds.get(index), index);
        }
        RemainingSerializedComments remainingSerializedComments = new RemainingSerializedComments(MAX_SERIALIZED_COMMENTS);
        List<Map<String, Object>> serializedThreads = new ArrayList<>();
        List<CommentThread> selectedThreads = commentThreadRepository.findWithCommentsByExerciseIdAndIdIn(exerciseId, orderedThreadIds).stream().filter(activeFilter)
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
        return serializedThreads;
    }

    /**
     * Renders the instructor-selected feedback across all repositories of the exercise as an instruction block for an adaptation run.
     *
     * @param exerciseId the exercise the threads belong to
     * @param threadIds  the thread ids the instructor selected
     * @return the instruction block followed by its JSON payload, or an empty string when nothing selected is still actionable, so the caller appends nothing
     */
    public String renderWholeExerciseSelectedFeedback(long exerciseId, Collection<Long> threadIds) {
        if (threadIds == null || threadIds.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> serializedThreads = serializeSelectedThreads(exerciseId, threadIds, thread -> !thread.isResolved() && !thread.isOutdated());
        if (serializedThreads.isEmpty()) {
            return "";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("threads", serializedThreads);
        return "Address the following selected instructor review feedback (each thread points at a file/line in one of the repositories). Apply the requested change for every "
                + "thread while keeping the rest of the exercise intact:\n" + serializePayload(payload, exerciseId);
    }

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

    /** Returns {@code null} rather than an empty payload for a thread that contributes no comments, so the caller can leave it out of the array entirely. */
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

    /** Newest comments win the budget, but the survivors are handed over in the order they were written, because a discussion only reads correctly forwards. */
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

    /** Returns {@code null} for a repository that review threads cannot point at, which means no selected thread can apply to it. */
    private CommentThreadLocationType mapRepositoryTypeToThreadLocationType(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> CommentThreadLocationType.TEMPLATE_REPO;
            case SOLUTION -> CommentThreadLocationType.SOLUTION_REPO;
            case TESTS -> CommentThreadLocationType.TEST_REPO;
            default -> null;
        };
    }

    /** Falls back to an empty thread list: prompt context is an optimisation, and failing to render it must not fail the generation or check that asked for it. */
    private String serializePayload(Map<String, Object> payload, long exerciseId) {
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e) {
            log.warn("Failed to serialize review-thread prompt context for exercise {}", exerciseId, e);
            return "{\"threads\":[]}";
        }
    }

    private String sanitizeAndNormalizeText(String text) {
        return normalizeWhitespace(HyperionUtils.sanitizeInput(text));
    }

    /** Folds a comment onto a single line with its breaks escaped, so one comment stays one JSON value however the instructor formatted it. */
    private String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n").trim();
    }

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
