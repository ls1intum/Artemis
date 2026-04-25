package de.tum.cit.aet.artemis.exercise.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.domain.review.ReviewThreadSyncAction;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.InlineCodeChangeDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseReviewThreadUpdateDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadGroupRepository;
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

    @Autowired
    private CommentThreadGroupRepository commentThreadGroupRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void initTest() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldCreateThreadWithInitialUserComment() throws Exception {
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
    void shouldRejectThreadWithMissingCommentText() throws Exception {
        TextExercise exercise = createExerciseWithVersion();

        CreateCommentThreadDTO dto = buildThreadDTO(new UserCommentContentDTO(null));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUserCommentWithMissingText() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);

        UserCommentContentDTO invalidComment = new UserCommentContentDTO(null);
        assertBadRequest(reviewCommentsPath(exercise.getId(), createdThread.id()), invalidComment, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUpdateWithMissingText() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        UserCommentContentDTO update = new UserCommentContentDTO(null);
        assertBadRequestWithPut(reviewCommentPath(exercise.getId(), initialComment.id()), update, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldCreateUserCommentWithUserContent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);

        UserCommentContentDTO reply = buildUserComment("Reply comment");
        CommentDTO savedReply = request.postWithResponseBody(reviewCommentsPath(exercise.getId(), createdThread.id()), reply, CommentDTO.class, HttpStatus.CREATED);

        assertThat(savedReply.type()).isEqualTo(CommentType.USER);
        assertThat(savedReply.content()).isInstanceOf(UserCommentContentDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldUpdateUserCommentContentWithUserContent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        UserCommentContentDTO update = new UserCommentContentDTO("Updated text");
        CommentDTO updated = request.putWithResponseBody(reviewCommentPath(exercise.getId(), initialComment.id()), update, CommentDTO.class, HttpStatus.OK);

        assertThat(updated.content()).isInstanceOf(UserCommentContentDTO.class);
        assertThat(((UserCommentContentDTO) updated.content()).text()).isEqualTo("Updated text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldMarkConsistencyInlineFixAsApplied() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO consistencyThread = createThreadWithConsistencyComment(exercise, "Consistency issue", false);
        CommentDTO consistencyComment = consistencyThread.comments().getFirst();
        clearInvocations(websocketMessagingService);

        var updated = request.performMvcRequest(MockMvcRequestBuilders.put(new URI(reviewCommentInlineFixAppliedPath(exercise.getId(), consistencyComment.id())))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn();
        CommentDTO updatedComment = objectMapper.readValue(updated.getResponse().getContentAsString(), CommentDTO.class);

        assertThat(updatedComment.type()).isEqualTo(CommentType.CONSISTENCY_CHECK);
        assertThat(updatedComment.content()).isInstanceOf(ConsistencyIssueCommentContentDTO.class);
        InlineCodeChangeDTO inlineFix = ((ConsistencyIssueCommentContentDTO) updatedComment.content()).suggestedFix();
        assertThat(inlineFix).isNotNull();
        assertThat(inlineFix.applied()).isTrue();

        ArgumentCaptor<Object> websocketPayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, times(1)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), websocketPayloadCaptor.capture());
        ExerciseReviewThreadUpdateDTO websocketPayload = (ExerciseReviewThreadUpdateDTO) websocketPayloadCaptor.getValue();
        assertThat(websocketPayload.action()).isEqualTo(ReviewThreadSyncAction.COMMENT_UPDATED);
        assertThat(websocketPayload.comment().id()).isEqualTo(consistencyComment.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldRejectMarkConsistencyInlineFixAsAppliedForUserComment() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");
        CommentDTO userComment = createdThread.comments().getFirst();

        var result = request.performMvcRequest(
                MockMvcRequestBuilders.put(new URI(reviewCommentInlineFixAppliedPath(exercise.getId(), userComment.id()))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = objectMapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo("error.inlineFixNotSupported");
        assertThat(payload.get("params")).isEqualTo("exerciseReviewComment");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldDeleteThreadWhenLastCommentRemoved() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO initialComment = createdThread.comments().getFirst();

        request.delete(reviewCommentPath(exercise.getId(), initialComment.id()), HttpStatus.NO_CONTENT);

        assertThat(commentRepository.findById(initialComment.id())).isEmpty();
        assertThat(commentThreadRepository.findById(createdThread.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldReturnThreadsWithCommentsAndMetadata() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class, HttpStatus.CREATED);

        var threads = request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.OK, CommentThreadDTO.class);
        assertThat(threads).hasSize(1);
        assertThat(threads.getFirst().comments()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldUpdateThreadResolvedState() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);

        UpdateThreadResolvedStateDTO update = new UpdateThreadResolvedStateDTO(true);
        CommentThreadDTO resolved = request.putWithResponseBody(reviewThreadResolvedPath(exercise.getId(), createdThread.id()), update, CommentThreadDTO.class, HttpStatus.OK);

        assertThat(resolved.resolved()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldUpdateThreadGroupResolvedState() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO second = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Second")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO ungrouped = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Ungrouped")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), second.id()));
        CommentThreadGroupDTO group = request.postWithResponseBody(reviewThreadGroupsPath(exercise.getId()), groupRequest, CommentThreadGroupDTO.class, HttpStatus.CREATED);

        UpdateThreadResolvedStateDTO update = new UpdateThreadResolvedStateDTO(true);
        clearInvocations(websocketMessagingService);
        var mvcResult = request.performMvcRequest(MockMvcRequestBuilders.put(new URI(reviewThreadGroupResolvedPath(exercise.getId(), group.id())))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update))).andExpect(status().isOk()).andReturn();
        List<CommentThreadDTO> updatedGroupThreads = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });

        assertThat(updatedGroupThreads).hasSize(2).allMatch(CommentThreadDTO::resolved);

        var threads = request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.OK, CommentThreadDTO.class);
        assertThat(threads.stream().filter(thread -> List.of(first.id(), second.id()).contains(thread.id()))).allMatch(CommentThreadDTO::resolved);
        assertThat(threads.stream().filter(thread -> thread.id().equals(ungrouped.id())).findFirst().orElseThrow().resolved()).isFalse();

        ArgumentCaptor<Object> websocketPayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, times(2)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), websocketPayloadCaptor.capture());
        List<ExerciseReviewThreadUpdateDTO> websocketPayloads = websocketPayloadCaptor.getAllValues().stream().map(ExerciseReviewThreadUpdateDTO.class::cast).toList();
        assertThat(websocketPayloads).allMatch(payload -> payload.action() == ReviewThreadSyncAction.THREAD_UPDATED).extracting(payload -> payload.thread().id())
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadWithoutInitialComment() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, null, null, 1, null);

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadCreationWithNullBody() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        assertBadRequestWithNullBody(reviewThreadsPath(exercise.getId()), "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadWithoutInitialLineNumber() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, null, null, null, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectRepositoryThreadWithoutInitialFilePath() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, null, null, 1, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.initialFilePathMissing", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectRepositoryThreadWithBlankInitialFilePath() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, null, "   ", 1, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.initialFilePathBlank", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectRepositoryThreadWithTooLongInitialFilePath() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, null, "a".repeat(1025), 1, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldAllowProblemStatementThreadWithoutFilePath() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, null, null, 1, buildUserComment("Text"));

        CommentThreadDTO created = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), dto, CommentThreadDTO.class, HttpStatus.CREATED);
        assertThat(created.initialFilePath()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectAuxiliaryRepositoryIdWhenTargetIsNotAuxiliaryRepo() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, 1L, null, 1, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.auxiliaryRepositoryNotAllowed", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectMissingAuxiliaryRepositoryIdForAuxiliaryTarget() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.AUXILIARY_REPO, null, "file.txt", 1, buildUserComment("Text"));

        assertBadRequest(reviewThreadsPath(exercise.getId()), dto, "error.auxiliaryRepositoryMissing", THREAD_ENTITY_NAME);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUseLatestVersionForReplyCommentInitialVersion() throws Exception {
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
    void shouldForbidThreadCreationForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        long beforeCount = commentThreadRepository.count();

        request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class, HttpStatus.FORBIDDEN);

        assertThat(commentThreadRepository.count()).isEqualTo(beforeCount);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidThreadListForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();

        request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.FORBIDDEN, CommentThreadDTO.class);

        assertThat(commentThreadRepository.findWithCommentsByExerciseId(exercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidThreadResolvedUpdateForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");

        UpdateThreadResolvedStateDTO update = new UpdateThreadResolvedStateDTO(true);
        request.putWithResponseBody(reviewThreadResolvedPath(exercise.getId(), createdThread.id()), update, CommentThreadDTO.class, HttpStatus.FORBIDDEN);

        CommentThread persisted = commentThreadRepository.findById(createdThread.id()).orElseThrow();
        assertThat(persisted.isResolved()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidThreadGroupResolvedUpdateForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThread first = commentThreadRepository.save(buildThreadEntity(exercise));
        CommentThread second = commentThreadRepository.save(buildThreadEntity(exercise));
        CommentThreadGroup group = new CommentThreadGroup();
        group.setExercise(exercise);
        group = commentThreadGroupRepository.save(group);
        first.setGroup(group);
        second.setGroup(group);
        commentThreadRepository.saveAll(List.of(first, second));

        UpdateThreadResolvedStateDTO update = new UpdateThreadResolvedStateDTO(true);
        request.performMvcRequest(MockMvcRequestBuilders.put(new URI(reviewThreadGroupResolvedPath(exercise.getId(), group.getId()))).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update))).andExpect(status().isForbidden());

        assertThat(commentThreadRepository.findById(first.getId())).get().extracting(CommentThread::isResolved).isEqualTo(false);
        assertThat(commentThreadRepository.findById(second.getId())).get().extracting(CommentThread::isResolved).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidCommentUpdateForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");
        CommentDTO initialComment = createdThread.comments().getFirst();

        UserCommentContentDTO update = new UserCommentContentDTO("Updated text");
        request.putWithResponseBody(reviewCommentPath(exercise.getId(), initialComment.id()), update, CommentDTO.class, HttpStatus.FORBIDDEN);

        Comment persisted = commentRepository.findById(initialComment.id()).orElseThrow();
        assertThat(persisted.getContent()).isInstanceOf(UserCommentContentDTO.class);
        assertThat(((UserCommentContentDTO) persisted.getContent()).text()).isEqualTo("Initial comment");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidCommentDeleteForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = createThreadWithComment(exercise, "Initial comment");
        CommentDTO initialComment = createdThread.comments().getFirst();

        request.delete(reviewCommentPath(exercise.getId(), initialComment.id()), HttpStatus.FORBIDDEN);

        assertThat(commentRepository.findById(initialComment.id())).isPresent();
        assertThat(commentThreadRepository.findById(createdThread.id())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldKeepThreadWhenOtherCommentsRemain() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO createdThread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")),
                CommentThreadDTO.class, HttpStatus.CREATED);
        CommentDTO reply = request.postWithResponseBody(reviewCommentsPath(exercise.getId(), createdThread.id()), buildUserComment("Reply"), CommentDTO.class, HttpStatus.CREATED);

        request.delete(reviewCommentPath(exercise.getId(), reply.id()), HttpStatus.NO_CONTENT);

        assertThat(commentRepository.findById(createdThread.comments().getFirst().id())).isPresent();
        assertThat(commentRepository.findById(reply.id())).isEmpty();
        assertThat(commentThreadRepository.findById(createdThread.id())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnThreadsWithCommentsInOrder() throws Exception {
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldCreateThreadGroupWithTwoThreads() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO second = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Second")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), second.id()));
        CommentThreadGroupDTO group = request.postWithResponseBody(reviewThreadGroupsPath(exercise.getId()), groupRequest, CommentThreadGroupDTO.class, HttpStatus.CREATED);

        assertThat(group.exerciseId()).isEqualTo(exercise.getId());
        assertThat(group.threadIds()).containsExactlyInAnyOrder(first.id(), second.id());

        var threads = request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.OK, CommentThreadDTO.class);
        assertThat(threads).allMatch(thread -> thread.groupId() != null && thread.groupId().equals(group.id()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void shouldDeleteThreadGroupKeepsThreads() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO second = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Second")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), second.id()));
        CommentThreadGroupDTO group = request.postWithResponseBody(reviewThreadGroupsPath(exercise.getId()), groupRequest, CommentThreadGroupDTO.class, HttpStatus.CREATED);

        request.delete(reviewThreadGroupPath(exercise.getId(), group.id()), HttpStatus.NO_CONTENT);

        var threads = request.getList(reviewThreadsPath(exercise.getId()), HttpStatus.OK, CommentThreadDTO.class);
        assertThat(threads).hasSize(2);
        assertThat(threads).allMatch(thread -> thread.groupId() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteThreadGroupWhenLastThreadDeleted() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO second = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Second")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), second.id()));
        CommentThreadGroupDTO group = request.postWithResponseBody(reviewThreadGroupsPath(exercise.getId()), groupRequest, CommentThreadGroupDTO.class, HttpStatus.CREATED);

        request.delete(reviewCommentPath(exercise.getId(), first.comments().getFirst().id()), HttpStatus.NO_CONTENT);
        assertThat(commentThreadGroupRepository.findById(group.id())).isPresent();

        request.delete(reviewCommentPath(exercise.getId(), second.comments().getFirst().id()), HttpStatus.NO_CONTENT);
        assertThat(commentThreadGroupRepository.findById(group.id())).isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupWithTooFewThreads() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id()));
        assertBadRequest(reviewThreadGroupsPath(exercise.getId()), groupRequest, "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupCreationWithNullBody() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        assertBadRequestWithNullBody(reviewThreadGroupsPath(exercise.getId()), "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupWithThreadFromDifferentExercise() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        var otherCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise otherExercise = ExerciseUtilService.getFirstExerciseWithType(otherCourse, TextExercise.class);
        exerciseVersionService.createExerciseVersion(otherExercise);
        CommentThreadDTO otherThread = request.postWithResponseBody(reviewThreadsPath(otherExercise.getId()), buildThreadDTO(buildUserComment("Second")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), otherThread.id()));
        assertBadRequest(reviewThreadGroupsPath(exercise.getId()), groupRequest, "error.exerciseMismatch", "exerciseReviewCommentThread");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupWithAlreadyGroupedThread() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO second = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Second")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentThreadDTO third = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Third")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO initialGroup = new CreateCommentThreadGroupDTO(List.of(first.id(), second.id()));
        request.postWithResponseBody(reviewThreadGroupsPath(exercise.getId()), initialGroup, CommentThreadGroupDTO.class, HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), third.id()));
        assertBadRequest(reviewThreadGroupsPath(exercise.getId()), groupRequest, "error.threadGrouped", "exerciseReviewCommentThread");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupWithMissingThread() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO first = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("First")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(first.id(), Long.MAX_VALUE));
        assertBadRequest(reviewThreadGroupsPath(exercise.getId()), groupRequest, "error.threadMissing", "exerciseReviewCommentThread");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidThreadGroupCreationForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CreateCommentThreadGroupDTO groupRequest = new CreateCommentThreadGroupDTO(List.of(1L, 2L));
        request.postWithResponseBody(reviewThreadGroupsPath(exercise.getId()), groupRequest, CommentThreadGroupDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectCommentCreationWithNullBody() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO thread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        assertBadRequestWithNullBody(reviewCommentsPath(exercise.getId(), thread.id()), "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectResolvedUpdateWithNullBody() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO thread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class,
                HttpStatus.CREATED);

        assertBadRequestWithPutNullBody(reviewThreadResolvedPath(exercise.getId(), thread.id()), "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectCommentUpdateWithNullBody() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThreadDTO thread = request.postWithResponseBody(reviewThreadsPath(exercise.getId()), buildThreadDTO(buildUserComment("Initial comment")), CommentThreadDTO.class,
                HttpStatus.CREATED);
        CommentDTO comment = thread.comments().getFirst();

        assertBadRequestWithPutNullBody(reviewCommentPath(exercise.getId(), comment.id()), "error.http.400", null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldForbidThreadGroupDeletionForStudent() throws Exception {
        TextExercise exercise = createExerciseWithVersion();
        CommentThread first = commentThreadRepository.save(buildThreadEntity(exercise));
        CommentThread second = commentThreadRepository.save(buildThreadEntity(exercise));
        CommentThreadGroup group = new CommentThreadGroup();
        group.setExercise(exercise);
        group = commentThreadGroupRepository.save(group);
        first.setGroup(group);
        second.setGroup(group);
        commentThreadRepository.saveAll(List.of(first, second));

        request.delete(reviewThreadGroupPath(exercise.getId(), group.getId()), HttpStatus.FORBIDDEN);
    }

    private TextExercise createExerciseWithVersion() {
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        exerciseVersionService.createExerciseVersion(exercise);
        return exercise;
    }

    private CreateCommentThreadDTO buildThreadDTO(UserCommentContentDTO initialComment) {
        return new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, null, null, 1, initialComment);
    }

    private UserCommentContentDTO buildUserComment(String text) {
        return new UserCommentContentDTO(text);
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

    private String reviewCommentInlineFixAppliedPath(long exerciseId, long commentId) {
        return reviewCommentPath(exerciseId, commentId) + "/inline-fix/applied";
    }

    private String reviewThreadResolvedPath(long exerciseId, long threadId) {
        return reviewThreadsPath(exerciseId) + "/" + threadId + "/resolved";
    }

    private String reviewThreadGroupsPath(long exerciseId) {
        return "/api/exercise/exercises/" + exerciseId + "/review-thread-groups";
    }

    private String reviewThreadGroupPath(long exerciseId, long groupId) {
        return reviewThreadGroupsPath(exerciseId) + "/" + groupId;
    }

    private String reviewThreadGroupResolvedPath(long exerciseId, long groupId) {
        return reviewThreadGroupPath(exerciseId, groupId) + "/resolved";
    }

    private void assertBadRequest(String path, Object body, String expectedMessage, String expectedParams) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(body);
        var result = request.performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().isBadRequest()).andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = objectMapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo(expectedMessage);
        if (expectedParams != null) {
            assertThat(payload.get("params")).isEqualTo(expectedParams);
        }
    }

    private void assertBadRequestWithPut(String path, Object body, String expectedMessage, String expectedParams) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(body);
        var result = request.performMvcRequest(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().isBadRequest()).andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = objectMapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo(expectedMessage);
        if (expectedParams != null) {
            assertThat(payload.get("params")).isEqualTo(expectedParams);
        }
    }

    private void assertBadRequestWithNullBody(String path, String expectedMessage, String expectedParams) throws Exception {
        var result = request.performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest()).andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = objectMapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo(expectedMessage);
        if (expectedParams != null) {
            assertThat(payload.get("params")).isEqualTo(expectedParams);
        }
    }

    private void assertBadRequestWithPutNullBody(String path, String expectedMessage, String expectedParams) throws Exception {
        var result = request.performMvcRequest(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content("null")).andExpect(status().isBadRequest())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        var payload = objectMapper.readValue(content, java.util.Map.class);
        assertThat(payload.get("message")).isEqualTo(expectedMessage);
        if (expectedParams != null) {
            assertThat(payload.get("params")).isEqualTo(expectedParams);
        }
    }

    private CommentThreadDTO createThreadWithComment(TextExercise exercise, String text) {
        var thread = new CommentThread();
        thread.setExercise(exercise);
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialFilePath(null);
        thread.setInitialLineNumber(1);
        thread.setFilePath(null);
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

    private CommentThreadDTO createThreadWithConsistencyComment(TextExercise exercise, String text, boolean applied) {
        var thread = new CommentThread();
        thread.setExercise(exercise);
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialFilePath(null);
        thread.setInitialLineNumber(1);
        thread.setFilePath(null);
        thread.setLineNumber(1);
        thread.setOutdated(false);
        thread.setResolved(false);
        thread.setInitialVersion(exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null));
        var savedThread = commentThreadRepository.save(thread);

        var comment = new Comment();
        comment.setThread(savedThread);
        comment.setType(CommentType.CONSISTENCY_CHECK);
        comment.setContent(new ConsistencyIssueCommentContentDTO(Severity.HIGH, ConsistencyIssueCategory.IDENTIFIER_NAMING_INCONSISTENCY, text,
                new InlineCodeChangeDTO(1, 1, "old", "new", applied)));
        comment.setInitialVersion(savedThread.getInitialVersion());
        var savedComment = commentRepository.save(comment);

        return new CommentThreadDTO(savedThread, List.of(new CommentDTO(savedComment)));
    }

    private CommentThread buildThreadEntity(TextExercise exercise) {
        CommentThread thread = new CommentThread();
        thread.setExercise(exercise);
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialFilePath(null);
        thread.setInitialLineNumber(1);
        thread.setFilePath(null);
        thread.setLineNumber(1);
        thread.setOutdated(false);
        thread.setResolved(false);
        thread.setInitialVersion(exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null));

        return thread;
    }

    private CommentThreadGroup buildGroupEntity(TextExercise exercise, List<CommentThread> threads) {
        CommentThreadGroup group = new CommentThreadGroup();
        group.setExercise(exercise);
        group.getThreads().addAll(threads);
        return group;
    }
}
