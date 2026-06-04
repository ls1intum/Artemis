package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class HyperionReviewCommentContextRendererServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private CommentThreadRepository commentThreadRepository;

    private HyperionReviewCommentContextRendererService contextRendererService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        contextRendererService = new HyperionReviewCommentContextRendererService(commentThreadRepository, OBJECT_MAPPER);
    }

    @Test
    void renderCodeGenerationSelectedFeedback_limitsSelectedThreadIdsBeforeQuery() {
        int maxSelectedThreads = (int) ReflectionTestUtils.getField(HyperionReviewCommentContextRendererService.class, "MAX_SELECTED_FEEDBACK_THREADS");
        List<Long> threadIds = new ArrayList<>();
        threadIds.add(1L);
        threadIds.add(2L);
        threadIds.add(null);
        threadIds.add(2L);
        for (long id = 3; id <= maxSelectedThreads + 10L; id++) {
            threadIds.add(id);
        }

        when(commentThreadRepository.findWithCommentsByExerciseIdAndIdIn(eq(7L), anyCollection())).thenReturn(List.of());

        contextRendererService.renderCodeGenerationSelectedFeedback(7L, RepositoryType.SOLUTION, threadIds);

        ArgumentCaptor<Collection<Long>> threadIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(commentThreadRepository).findWithCommentsByExerciseIdAndIdIn(eq(7L), threadIdsCaptor.capture());

        assertThat(threadIdsCaptor.getValue()).containsExactlyElementsOf(threadIds.stream().filter(id -> id != null).distinct().limit(maxSelectedThreads).toList());
        assertThat(threadIdsCaptor.getValue()).hasSize(maxSelectedThreads);
    }

    @Test
    void renderCodeGenerationSelectedFeedback_appliesGlobalCommentBudgetAcrossThreads() throws Exception {
        int maxSerializedComments = (int) ReflectionTestUtils.getField(HyperionReviewCommentContextRendererService.class, "MAX_SERIALIZED_COMMENTS");
        CommentThread firstThread = createThread(11L, CommentThreadLocationType.SOLUTION_REPO, maxSerializedComments);
        CommentThread secondThread = createThread(12L, CommentThreadLocationType.SOLUTION_REPO, 5);
        when(commentThreadRepository.findWithCommentsByExerciseIdAndIdIn(9L, List.of(11L, 12L))).thenReturn(List.of(firstThread, secondThread));

        String result = contextRendererService.renderCodeGenerationSelectedFeedback(9L, RepositoryType.SOLUTION, List.of(11L, 12L));

        JsonNode payload = OBJECT_MAPPER.readTree(result);
        assertThat(payload.path("threads")).hasSize(1);
        assertThat(payload.path("threads").get(0).path("id").asLong()).isEqualTo(11L);
        assertThat(payload.path("threads").get(0).path("comments")).hasSize(maxSerializedComments);
    }

    @Test
    void renderCodeGenerationSelectedFeedback_keepsNewestCommentsWithinBudgetInChronologicalOrder() throws Exception {
        int maxSerializedComments = (int) ReflectionTestUtils.getField(HyperionReviewCommentContextRendererService.class, "MAX_SERIALIZED_COMMENTS");
        CommentThread thread = createThread(13L, CommentThreadLocationType.SOLUTION_REPO, maxSerializedComments + 2);
        when(commentThreadRepository.findWithCommentsByExerciseIdAndIdIn(10L, List.of(13L))).thenReturn(List.of(thread));

        String result = contextRendererService.renderCodeGenerationSelectedFeedback(10L, RepositoryType.SOLUTION, List.of(13L));

        JsonNode comments = OBJECT_MAPPER.readTree(result).path("threads").get(0).path("comments");
        assertThat(comments).hasSize(maxSerializedComments);
        assertThat(comments.get(0).path("text").asText()).isEqualTo("comment-13-2");
        assertThat(comments.get(maxSerializedComments - 1).path("text").asText()).isEqualTo("comment-13-" + (maxSerializedComments + 1));
    }

    private CommentThread createThread(long threadId, CommentThreadLocationType targetType, int commentCount) {
        CommentThread thread = new CommentThread();
        thread.setId(threadId);
        thread.setTargetType(targetType);
        thread.setFilePath("src/test/File" + threadId + ".java");
        thread.setLineNumber(10);
        thread.setResolved(false);
        thread.setOutdated(false);

        for (int index = 0; index < commentCount; index++) {
            Comment comment = new Comment();
            comment.setId(threadId * 1000 + index);
            comment.setType(CommentType.USER);
            comment.setCreatedDate(Instant.parse("2026-01-01T10:00:00Z").plusSeconds(index));
            comment.setContent(new UserCommentContentDTO("comment-" + threadId + "-" + index));
            thread.getComments().add(comment);
        }

        return thread;
    }
}
