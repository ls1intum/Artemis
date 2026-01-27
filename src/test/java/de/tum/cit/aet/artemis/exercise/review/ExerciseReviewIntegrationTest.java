package de.tum.cit.aet.artemis.exercise.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseReviewIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "reviewcommentresource";

    private static final String THREAD_ENTITY_NAME = "exerciseReviewCommentThread";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private CommentThreadRepository commentThreadRepository;

    @Autowired
    private CommentRepository commentRepository;

    @BeforeEach
    void initTest() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createThread_withInitialUserComment_createsThreadAndComment() throws Exception {
        TextExercise exercise = createExerciseWithVersion();

        CreateCommentThreadDTO dto = buildThreadDTO(buildUserComment("Initial comment"));
        CommentThreadDTO created = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), dto, CommentThreadDTO.class, HttpStatus.CREATED);

        assertThat(created).isNotNull();
        assertThat(created.comments()).hasSize(1);
        CommentDTO comment = created.comments().getFirst();
        assertThat(comment.type()).isEqualTo(CommentType.USER);
        assertThat(comment.content()).isInstanceOf(UserCommentContentDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createThread_withNonUserComment_rejected() throws Exception {
        TextExercise exercise = createExerciseWithVersion();

        CreateCommentDTO invalidComment = new CreateCommentDTO(CommentType.CONSISTENCY_CHECK, buildConsistencyIssueContent());
        CreateCommentThreadDTO dto = buildThreadDTO(invalidComment);

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.commentTypeNotSupported", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createUserComment_withNonUserContent_rejected() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);

        CreateCommentDTO invalidComment = new CreateCommentDTO(CommentType.USER, buildConsistencyIssueContent());
        assertBadRequest(reviewCommentsPath(exercise.getId(), createdThread.id()), invalidComment, "error.commentContentNotSupported", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateUserCommentContent_withNonUserContent_rejected() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        UpdateCommentContentDTO update = new UpdateCommentContentDTO(buildConsistencyIssueContent());
        assertBadRequestWithPut(reviewCommentPath(exercise.getId(), initialComment.id()), update, "error.commentContentNotSupported", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createUserComment_withUserContent_succeeds() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);

        CreateCommentDTO reply = buildUserComment("Reply comment");
        CommentDTO savedReply = request.postWithResponseBody(reviewCommentsPath(exercise.getId(), createdThread.id()), reply, CommentDTO.class, HttpStatus.CREATED);

        assertThat(savedReply.type()).isEqualTo(CommentType.USER);
        assertThat(savedReply.content()).isInstanceOf(UserCommentContentDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateUserCommentContent_withUserContent_succeeds() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        UpdateCommentContentDTO update = new UpdateCommentContentDTO(new UserCommentContentDTO("Updated text"));
        CommentDTO updated = request.putWithResponseBody(reviewCommentPath(exercise.getId(), initialComment.id()), update, CommentDTO.class, HttpStatus.OK);

        assertThat(updated.content()).isInstanceOf(UserCommentContentDTO.class);
        assertThat(((UserCommentContentDTO) updated.content()).text()).isEqualTo("Updated text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteComment_removesThreadWhenLastComment() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        request.delete(reviewCommentPath(exercise.getId(), initialComment.id()), HttpStatus.OK);

        assertThat(commentRepository.countByThreadId(createdThread.id())).isZero();
        assertThat(commentThreadRepository.findById(createdThread.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getThreads_returnsCommentsAndMetadata() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class, HttpStatus.CREATED);

        var threads = request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.OK, CommentThreadDTO.class);
        assertThat(threads).hasSize(1);
        assertThat(threads.getFirst().comments()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateThreadResolvedState_succeeds() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);

        UpdateThreadResolvedStateDTO update = new UpdateThreadResolvedStateDTO(true);
        CommentThreadDTO resolved = request.putWithResponseBody(reviewThreadResolvedPath(exercise.getId(), createdThread.id()), update, CommentThreadDTO.class, HttpStatus.OK);

        assertThat(resolved.resolved()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createThread_withoutInitialComment_rejected() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(null, CommentThreadLocationType.PROBLEM_STATEMENT, null, "problem_statement.md", 1, null);

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.validation", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createThread_missingInitialLocation_rejected() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(null, CommentThreadLocationType.PROBLEM_STATEMENT, null, null, null, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.initialLocationMissing", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createThread_repositoryTarget_missingLocation_rejected() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(null, CommentThreadLocationType.TEMPLATE_REPO, null, null, null, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.initialLocationMissing", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void commentInitialVersion_followsThreadAndLatestVersion() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        long initialVersionId = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow().getId();

        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        assertThat(initialComment.initialVersionId()).isEqualTo(initialVersionId);

        exercise.setProblemStatement("Updated problem statement");
        exerciseRepository.save(exercise);
        exerciseVersionService.createExerciseVersion(exercise);
        long latestVersionId = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow().getId();

        CommentDTO reply = request.postWithResponseBody(reviewCommentsPath(exercise.getId(), createdThread.id()), buildUserComment("Reply"), CommentDTO.class, HttpStatus.CREATED);
        assertThat(reply.initialVersionId()).isEqualTo(latestVersionId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createThread_forbiddenForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        long beforeCount = commentThreadRepository.count();

        request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class, HttpStatus.FORBIDDEN);

        assertThat(commentThreadRepository.count()).isEqualTo(beforeCount);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getThreads_forbiddenForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();

        request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.FORBIDDEN, CommentThreadDTO.class);

        assertThat(commentThreadRepository.findByExerciseId(exercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateThreadResolvedState_forbiddenForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");

        UpdateThreadResolvedStateDTO update = new UpdateThreadResolvedStateDTO(true);
        request.putWithResponseBody(reviewThreadResolvedPath(exercise.getId(), createdThread.id()), update, CommentThreadDTO.class, HttpStatus.FORBIDDEN);

        CommentThread persisted = commentThreadRepository.findById(createdThread.id()).orElseThrow();
        assertThat(persisted.isResolved()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateComment_forbiddenForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");
        CommentDTO initialComment = createdThread.comments().getFirst();

        UpdateCommentContentDTO update = new UpdateCommentContentDTO(new UserCommentContentDTO("Updated text"));
        request.putWithResponseBody(reviewCommentPath(exercise.getId(), initialComment.id()), update, CommentDTO.class, HttpStatus.FORBIDDEN);

        Comment persisted = commentRepository.findById(initialComment.id()).orElseThrow();
        assertThat(persisted.getContent()).isInstanceOf(UserCommentContentDTO.class);
        assertThat(((UserCommentContentDTO) persisted.getContent()).text()).isEqualTo("Initial comment");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteComment_forbiddenForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");
        CommentDTO initialComment = createdThread.comments().getFirst();

        request.delete(reviewCommentPath(exercise.getId(), initialComment.id()), HttpStatus.FORBIDDEN);

        assertThat(commentRepository.findById(initialComment.id())).isPresent();
        assertThat(commentThreadRepository.findById(createdThread.id())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteComment_keepsThreadWhenCommentsRemain() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO reply = request.postWithResponseBody(reviewCommentsPath(exercise.getId(), createdThread.id()), buildUserComment("Reply"), CommentDTO.class, HttpStatus.CREATED);

        request.delete(reviewCommentPath(exercise.getId(), reply.id()), HttpStatus.OK);

        assertThat(commentRepository.countByThreadId(createdThread.id())).isEqualTo(1);
        assertThat(commentThreadRepository.findById(createdThread.id())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getThreads_returnsCommentsInOrder() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO reply = request.postWithResponseBody(reviewCommentsPath(exercise.getId(), createdThread.id()), buildUserComment("Reply"), CommentDTO.class, HttpStatus.CREATED);

        var threads = request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.OK, CommentThreadDTO.class);
        CommentThreadDTO thread = threads.getFirst();
        assertThat(thread.comments()).hasSize(2);
        assertThat(thread.comments().getFirst().id()).isEqualTo(createdThread.comments().getFirst().id());
        assertThat(thread.comments().get(1).id()).isEqualTo(reply.id());
    }

    private TextExercise createExerciseWithVersion() {
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        exerciseVersionService.createExerciseVersion(exercise);
        return exercise;
    }

    private CreateCommentThreadDTO buildThreadDTO(CreateCommentDTO initialComment) {
        return new CreateCommentThreadDTO(null, CommentThreadLocationType.PROBLEM_STATEMENT, null, "problem_statement.md", 1, initialComment);
    }

    private CreateCommentDTO buildUserComment(String text) {
        return new CreateCommentDTO(CommentType.USER, new UserCommentContentDTO(text));
    }

    private ConsistencyIssueCommentContentDTO buildConsistencyIssueContent() {
        return new ConsistencyIssueCommentContentDTO(Severity.LOW, ConsistencyIssueCategory.METHOD_RETURN_TYPE_MISMATCH, "Issue text", null);
    }

    private String reviewThreadsPath(long exerciseId) {
        return "/api/exercise/exercises/" + exerciseId + "/review-threads";
    }

    private String reviewCommentsPath(long exerciseId, long threadId) {
        return reviewThreadsPath(exerciseId) + "/" + threadId + "/comments";
    }

    private String reviewCommentPath(long exerciseId, long commentId) {
        return "/api/exercise/exercises/" + exerciseId + "/review-comments/" + commentId;
    }

    private String reviewThreadResolvedPath(long exerciseId, long threadId) {
        return reviewThreadsPath(exerciseId) + "/" + threadId + "/resolved";
    }

    private void assertBadRequest(String path, Object body, String expectedMessage, String expectedParams) throws Exception {
        ObjectMapper mapper = (ObjectMapper) ReflectionTestUtils.getField(request, "mapper");
        String jsonBody = mapper.writeValueAsString(body);
        var result = request.performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().isBadRequest()).andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = mapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo(expectedMessage);
        if (expectedParams != null) {
            assertThat(payload.get("params")).isEqualTo(expectedParams);
        }
    }

    private void assertBadRequestWithPut(String path, Object body, String expectedMessage, String expectedParams) throws Exception {
        ObjectMapper mapper = (ObjectMapper) ReflectionTestUtils.getField(request, "mapper");
        String jsonBody = mapper.writeValueAsString(body);
        var result = request.performMvcRequest(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().isBadRequest()).andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = mapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo(expectedMessage);
        if (expectedParams != null) {
            assertThat(payload.get("params")).isEqualTo(expectedParams);
        }
    }

    private CommentThreadDTO createThreadWithComment(TextExercise exercise, String text) {
        var thread = new CommentThread();
        thread.setExercise(exercise);
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialFilePath("problem_statement.md");
        thread.setInitialLineNumber(1);
        thread.setFilePath("problem_statement.md");
        thread.setLineNumber(1);
        thread.setOutdated(false);
        thread.setResolved(false);
        thread.setInitialVersion(exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null));
        var savedThread = commentThreadRepository.save(thread);

        var comment = new Comment();
        comment.setThread(savedThread);
        comment.setType(CommentType.USER);
        comment.setContent(new UserCommentContentDTO(text));
        comment.setInitialVersion(savedThread.getInitialVersion());
        var savedComment = commentRepository.save(comment);

        return new CommentThreadDTO(savedThread, List.of(new CommentDTO(savedComment)));
    }
}
