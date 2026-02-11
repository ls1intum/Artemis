package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService.LineMappingResult;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCService;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

class ExerciseReviewServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "reviewcommentservice";

    @Autowired
    private ExerciseReviewService exerciseReviewService;

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
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private LocalVCService localVCService;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private ProgrammingExercise programmingExercise;

    private final List<LocalRepoWithGit> createdRepos = new ArrayList<>();

    @BeforeEach
    void initTest() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        programmingExercise.setProblemStatement("Line 1\nLine 2\nLine 3");
        programmingExerciseRepository.save(programmingExercise);
    }

    @AfterEach
    void tearDown() {
        for (LocalRepoWithGit repo : createdRepos) {
            repo.git().close();
            FileUtils.deleteQuietly(repo.workingCopyPath().toFile());
        }
        createdRepos.clear();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnThreadsWhenInstructorRequestsByExerciseId() {
        CommentThread thread = persistThread(programmingExercise);
        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise otherExercise = ExerciseUtilService.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        persistThread(otherExercise);

        List<CommentThread> threads = exerciseReviewService.findThreadsByExerciseId(programmingExercise.getId());

        assertThat(threads).hasSize(1);
        assertThat(threads.getFirst().getId()).isEqualTo(thread.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void shouldThrowForbiddenWhenStudentRequestsThreads() {
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> exerciseReviewService.findThreadsByExerciseId(programmingExercise.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnThreadsWithCommentsWhenRequested() {
        CommentThread thread = persistThread(programmingExercise);
        Comment comment = buildUserCommentEntity("First");
        comment.setThread(thread);
        commentRepository.save(comment);

        Set<CommentThread> threads = exerciseReviewService.findThreadsWithCommentsByExerciseId(programmingExercise.getId());

        assertThat(threads).hasSize(1);
        CommentThread loaded = threads.iterator().next();
        assertThat(loaded.getComments()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnCommentsOrderedByCreatedDate() {
        CommentThread thread = persistThread(programmingExercise);

        Comment first = buildUserCommentEntity("First");
        first.setThread(thread);
        first.setCreatedDate(Instant.now().minusSeconds(10));
        Comment second = buildUserCommentEntity("Second");
        second.setThread(thread);
        second.setCreatedDate(Instant.now());
        commentRepository.save(first);
        commentRepository.save(second);

        List<Comment> comments = exerciseReviewService.findCommentsByThreadId(thread.getId());

        assertThat(comments).hasSize(2);
        assertThat(comments.getFirst().getContent()).isEqualTo(first.getContent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateThreadWhenValid() {
        CreateCommentThreadDTO dto = buildThreadDto();
        CommentThread saved = exerciseReviewService.createThread(programmingExercise.getId(), dto).thread();

        assertThat(saved.getExercise()).isNotNull();
        assertThat(saved.getExercise().getId()).isEqualTo(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateUserCommentWithAuthorAndInitialVersion() {
        ExerciseVersion initialVersion = createExerciseVersion();
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();

        UserCommentContentDTO comment = buildUserCommentContent("Initial");
        Comment saved = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), comment);

        assertThat(saved.getAuthor()).isNotNull();
        assertThat(saved.getInitialVersion()).isEqualTo(initialVersion);
        assertThat(saved.getInitialCommitSha()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUseLatestVersionForReplyComments() {
        ExerciseVersion initialVersion = createExerciseVersion();
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Initial"));

        programmingExercise.setProblemStatement("New Line\nLine 1\nLine 2\nLine 3");
        programmingExerciseRepository.save(programmingExercise);
        ExerciseVersion latestVersion = createExerciseVersion();

        Comment reply = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Reply"));

        assertThat(reply.getInitialVersion()).isEqualTo(latestVersion);
        assertThat(reply.getInitialVersion()).isNotEqualTo(initialVersion);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteThreadWhenLastCommentRemoved() {
        ExerciseReviewService.ThreadCreationResult creation = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto());
        CommentThread thread = creation.thread();
        Comment comment = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Only"));

        exerciseReviewService.deleteComment(programmingExercise.getId(), comment.getId());
        exerciseReviewService.deleteComment(programmingExercise.getId(), creation.comment().getId());

        assertThat(commentThreadRepository.findById(thread.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldKeepThreadWhenOtherCommentsExist() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        Comment first = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("First"));
        exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Second"));

        exerciseReviewService.deleteComment(programmingExercise.getId(), first.getId());

        assertThat(commentThreadRepository.findById(thread.getId())).isPresent();
        assertThat(commentRepository.findByThreadIdOrderByCreatedDateAsc(thread.getId())).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCountCommentsByThreadId() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("First"));
        exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Second"));

        long count = commentRepository.findByThreadIdOrderByCreatedDateAsc(thread.getId()).size();

        assertThat(count).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateThreadResolvedState() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();

        CommentThread updated = exerciseReviewService.updateThreadResolvedState(programmingExercise.getId(), thread.getId(), new UpdateThreadResolvedStateDTO(true));

        assertThat(updated.isResolved()).isTrue();
        CommentThread persisted = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(persisted.isResolved()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateThreadGroupWithTwoThreads() {
        CommentThread first = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        CommentThread second = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();

        var group = exerciseReviewService.createGroup(programmingExercise.getId(), new CreateCommentThreadGroupDTO(List.of(first.getId(), second.getId())));

        assertThat(group.getId()).isNotNull();
        assertThat(group.getExercise()).isEqualTo(programmingExercise);
        assertThat(commentThreadRepository.findById(first.getId())).get().extracting(CommentThread::getGroup).isEqualTo(group);
        assertThat(commentThreadRepository.findById(second.getId())).get().extracting(CommentThread::getGroup).isEqualTo(group);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteThreadGroupAndKeepThreads() {
        CommentThread first = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        CommentThread second = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();

        var group = exerciseReviewService.createGroup(programmingExercise.getId(), new CreateCommentThreadGroupDTO(List.of(first.getId(), second.getId())));
        exerciseReviewService.deleteGroup(programmingExercise.getId(), group.getId());

        assertThat(commentThreadGroupRepository.findById(group.getId())).isEmpty();
        assertThat(commentThreadRepository.findById(first.getId())).get().extracting(CommentThread::getGroup).isNull();
        assertThat(commentThreadRepository.findById(second.getId())).get().extracting(CommentThread::getGroup).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupWithTooFewThreads() {
        CommentThread first = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.createGroup(programmingExercise.getId(), new CreateCommentThreadGroupDTO(List.of(first.getId()))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadCreationWithNullBody() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewService.createThread(programmingExercise.getId(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadCreationWithInvalidInitialFilePath() {
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, null, "src/../invalid/path.java", 1, new UserCommentContentDTO("Initial"));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewService.createThread(programmingExercise.getId(), dto));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadGroupCreationWithNullBody() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewService.createGroup(programmingExercise.getId(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadResolvedUpdateWithNullBody() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.updateThreadResolvedState(programmingExercise.getId(), thread.getId(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldLoadThreadWithCommentsById() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("First"));

        Optional<CommentThread> loaded = commentThreadRepository.findWithCommentsById(thread.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getComments()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldLoadThreadsWithCommentsByExerciseId() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("First"));

        Set<CommentThread> threads = commentThreadRepository.findWithCommentsByExerciseId(programmingExercise.getId());

        assertThat(threads).hasSize(1);
        assertThat(threads.iterator().next().getComments()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldLoadCommentWithThreadAndExercise() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        Comment comment = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Initial"));

        Comment loaded = commentRepository.findWithThreadById(comment.getId()).orElseThrow();

        assertThat(loaded.getThread()).isNotNull();
        assertThat(loaded.getThread().getExercise()).isNotNull();
        assertThat(loaded.getThread().getExercise().getId()).isEqualTo(programmingExercise.getId());
        assertThat(loaded.getAuthor()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateCommentContentAndTimestamp() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        Comment comment = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Initial"));
        Instant previousModified = comment.getLastModifiedDate();

        Comment updated = exerciseReviewService.updateUserCommentContent(programmingExercise.getId(), comment.getId(), new UserCommentContentDTO("Updated"));

        assertThat(updated.getContent()).isInstanceOf(UserCommentContentDTO.class);
        assertThat(((UserCommentContentDTO) updated.getContent()).text()).isEqualTo("Updated");
        assertThat(updated.getLastModifiedDate()).isAfterOrEqualTo(previousModified);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectCommentWithoutContent() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUpdateWithMissingContent() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        Comment comment = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Initial"));

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.updateUserCommentContent(programmingExercise.getId(), comment.getId(), null));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectCreateUserCommentWhenExerciseIdDoesNotMatchThread() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        long mismatchingExerciseId = programmingExercise.getId() + 1;

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.createUserComment(mismatchingExerciseId, thread.getId(), buildUserCommentContent("Reply")));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectDeleteCommentWhenExerciseIdDoesNotMatchComment() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        Comment comment = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Reply"));
        long mismatchingExerciseId = programmingExercise.getId() + 1;

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewService.deleteComment(mismatchingExerciseId, comment.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUpdateResolvedStateWhenExerciseIdDoesNotMatchThread() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        long mismatchingExerciseId = programmingExercise.getId() + 1;

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.updateThreadResolvedState(mismatchingExerciseId, thread.getId(), new UpdateThreadResolvedStateDTO(true)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectUpdateCommentContentWhenExerciseIdDoesNotMatchComment() {
        CommentThread thread = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        Comment comment = exerciseReviewService.createUserComment(programmingExercise.getId(), thread.getId(), buildUserCommentContent("Initial"));
        long mismatchingExerciseId = programmingExercise.getId() + 1;

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.updateUserCommentContent(mismatchingExerciseId, comment.getId(), new UserCommentContentDTO("Updated")));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectDeleteGroupWhenExerciseIdDoesNotMatchGroup() {
        CommentThread first = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        CommentThread second = exerciseReviewService.createThread(programmingExercise.getId(), buildThreadDto()).thread();
        var group = exerciseReviewService.createGroup(programmingExercise.getId(), new CreateCommentThreadGroupDTO(List.of(first.getId(), second.getId())));
        long mismatchingExerciseId = programmingExercise.getId() + 1;

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewService.deleteGroup(mismatchingExerciseId, group.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNullCommitForProblemStatementThreads() {
        String commitSha = exerciseReviewService.resolveLatestCommitSha(CommentThreadLocationType.PROBLEM_STATEMENT, null, programmingExercise.getId());

        assertThat(commitSha).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResolveInitialVersionForProblemStatement() {
        ExerciseVersion initialVersion = createExerciseVersion();

        ExerciseVersion resolved = exerciseReviewService.resolveInitialVersion(CommentThreadLocationType.PROBLEM_STATEMENT, programmingExercise.getId());

        assertThat(resolved).isEqualTo(initialVersion);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResolveInitialVersionToNullForRepositoryTargets() {
        ExerciseVersion resolved = exerciseReviewService.resolveInitialVersion(CommentThreadLocationType.TEMPLATE_REPO, programmingExercise.getId());

        assertThat(resolved).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectInitialVersionWhenMissingProblemStatementVersion() {
        commentThreadRepository.deleteAll();
        exerciseVersionRepository.deleteAll();

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> exerciseReviewService.resolveInitialVersion(CommentThreadLocationType.PROBLEM_STATEMENT, programmingExercise.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnLatestCommitForTemplateRepo() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("template");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        var templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(repositoryUri.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();

        String commitSha = exerciseReviewService.resolveLatestCommitSha(CommentThreadLocationType.TEMPLATE_REPO, null, programmingExercise.getId());

        assertThat(commitSha).isEqualTo(gitService.getLastCommitHash(repositoryUri));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnLatestCommitForSolutionRepo() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("solution");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(repositoryUri.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();

        String commitSha = exerciseReviewService.resolveLatestCommitSha(CommentThreadLocationType.SOLUTION_REPO, null, programmingExercise.getId());

        assertThat(commitSha).isEqualTo(gitService.getLastCommitHash(repositoryUri));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnLatestCommitForTestRepo() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("tests");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        programmingExercise.setTestRepositoryUri(repositoryUri.toString());
        programmingExerciseRepository.save(programmingExercise);

        String commitSha = exerciseReviewService.resolveLatestCommitSha(CommentThreadLocationType.TEST_REPO, null, programmingExercise.getId());

        assertThat(commitSha).isEqualTo(gitService.getLastCommitHash(repositoryUri));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnLatestCommitForAuxiliaryRepo() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("aux");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("aux");
        auxiliaryRepository.setCheckoutDirectory("aux");
        auxiliaryRepository.setRepositoryUri(repositoryUri.toString());
        auxiliaryRepository.setExercise(programmingExercise);
        programmingExercise.getAuxiliaryRepositories().add(auxiliaryRepository);
        programmingExerciseRepository.save(programmingExercise);
        auxiliaryRepository = programmingExerciseRepository.findWithAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow().getAuxiliaryRepositories().getFirst();

        String commitSha = exerciseReviewService.resolveLatestCommitSha(CommentThreadLocationType.AUXILIARY_REPO, auxiliaryRepository.getId(), programmingExercise.getId());

        assertThat(commitSha).isEqualTo(gitService.getLastCommitHash(repositoryUri));
    }

    @Test
    void shouldMapLineInTextShiftsLinesAfterInsertion() {
        String oldText = "a\nb\nc\nd\n";
        String newText = "a\nb\nx\nc\nd\n";

        LineMappingResult result = exerciseReviewService.mapLineInText(oldText, newText, 3);

        assertThat(result.newLine()).isEqualTo(4);
        assertThat(result.outdated()).isFalse();
    }

    @Test
    void shouldMapLineInTextMarksLineOutdatedOnReplacement() {
        String oldText = "a\nb\nc\nd\n";
        String newText = "a\nb\nc2\nd\n";

        LineMappingResult result = exerciseReviewService.mapLineInText(oldText, newText, 3);

        assertThat(result.newLine()).isEqualTo(3);
        assertThat(result.outdated()).isTrue();
    }

    @Test
    void shouldMapLineInTextMarksInvalidLineAsOutdated() {
        LineMappingResult result = exerciseReviewService.mapLineInText("a\nb\n", "a\nb\n", 0);

        assertThat(result.newLine()).isNull();
        assertThat(result.outdated()).isTrue();
    }

    @Test
    void shouldMapLineBetweenCommitsInRepository() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("linemap");
        LocalVCRepositoryUri repositoryUri = repo.uri();

        Path filePath = repo.workingCopyPath().resolve("src").resolve("Main.java");
        Files.createDirectories(filePath.getParent());

        String oldText = String.join("\n", "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "");
        FileUtils.writeStringToFile(filePath.toFile(), oldText, StandardCharsets.UTF_8);
        repo.git().add().addFilepattern(".").call();
        RevCommit oldCommit = GitService.commit(repo.git()).setMessage("Add file").call();
        repo.git().push().setRemote("origin").call();

        String newText = String.join("\n", "alpha", "inserted", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta-updated", "iota", "kappa", "");
        FileUtils.writeStringToFile(filePath.toFile(), newText, StandardCharsets.UTF_8);
        repo.git().add().addFilepattern(".").call();
        RevCommit newCommit = GitService.commit(repo.git()).setMessage("Update file").call();
        repo.git().push().setRemote("origin").call();

        LineMappingResult shiftedLine = exerciseReviewService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 2);
        assertThat(shiftedLine.newLine()).isEqualTo(3);
        assertThat(shiftedLine.outdated()).isFalse();

        LineMappingResult editedLine = exerciseReviewService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 8);
        assertThat(editedLine.newLine()).isEqualTo(9);
        assertThat(editedLine.outdated()).isTrue();

        LineMappingResult unchangedLine = exerciseReviewService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 9);
        assertThat(unchangedLine.newLine()).isEqualTo(10);
        assertThat(unchangedLine.outdated()).isFalse();
    }

    @Test
    void shouldMapLineMarksDeletedFileAsOutdated() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("linemap-delete");
        LocalVCRepositoryUri repositoryUri = repo.uri();

        Path filePath = repo.workingCopyPath().resolve("src").resolve("Main.java");
        Files.createDirectories(filePath.getParent());
        FileUtils.writeStringToFile(filePath.toFile(), "a\nb\nc\n", StandardCharsets.UTF_8);
        repo.git().add().addFilepattern(".").call();
        RevCommit oldCommit = GitService.commit(repo.git()).setMessage("Add file").call();
        repo.git().push().setRemote("origin").call();

        Files.deleteIfExists(filePath);
        repo.git().add().addFilepattern(".").call();
        RevCommit newCommit = GitService.commit(repo.git()).setMessage("Delete file").call();
        repo.git().push().setRemote("origin").call();

        LineMappingResult result = exerciseReviewService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 1);
        assertThat(result.newLine()).isNull();
        assertThat(result.outdated()).isTrue();
    }

    @Test
    void shouldMapLineMarksAddedFileAsOutdatedWhenPreviouslyMissing() throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit("linemap-add");
        LocalVCRepositoryUri repositoryUri = repo.uri();

        RevCommit oldCommit = GitService.commit(repo.git()).setMessage("Initial").call();
        repo.git().push().setRemote("origin").call();

        Path filePath = repo.workingCopyPath().resolve("src").resolve("Main.java");
        Files.createDirectories(filePath.getParent());
        FileUtils.writeStringToFile(filePath.toFile(), "a\nb\nc\n", StandardCharsets.UTF_8);
        repo.git().add().addFilepattern(".").call();
        RevCommit newCommit = GitService.commit(repo.git()).setMessage("Add file").call();
        repo.git().push().setRemote("origin").call();

        LineMappingResult result = exerciseReviewService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 1);
        assertThat(result.newLine()).isNull();
        assertThat(result.outdated()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateThreadsForVersionChangeForTemplateRepoLines() throws Exception {
        RepoHistory history = createRepoWithTwoCommits("template-map");
        CommentThread thread = buildRepoThread(CommentThreadLocationType.TEMPLATE_REPO, "src/Main.java", 2);
        thread.setExercise(programmingExercise);
        commentThreadRepository.save(thread);

        var previousParticipation = new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, history.repositoryUri().toString(), null, history.oldCommit());
        var currentParticipation = new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, history.repositoryUri().toString(), null, history.newCommit());
        var previousProgramming = buildProgrammingSnapshot(null, null, previousParticipation, null, null);
        var currentProgramming = buildProgrammingSnapshot(null, null, currentParticipation, null, null);
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(3);
        assertThat(updated.isOutdated()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSkipUpdateThreadsForVersionChangeWhenTemplateRepoParticipationMissing() {
        CommentThread thread = buildRepoThread(CommentThreadLocationType.TEMPLATE_REPO, "src/Main.java", 5);
        thread.setExercise(programmingExercise);
        commentThreadRepository.save(thread);

        var previousParticipation = new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, null, null, "old");
        var currentParticipation = new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, "http://repo", null, null);
        var previousProgramming = buildProgrammingSnapshot(null, null, previousParticipation, null, null);
        var currentProgramming = buildProgrammingSnapshot(null, null, currentParticipation, null, null);
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(5);
        assertThat(updated.isOutdated()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateThreadsForVersionChangeForTestRepoLines() throws Exception {
        RepoHistory history = createRepoWithTwoCommits("test-map");
        CommentThread thread = buildRepoThread(CommentThreadLocationType.TEST_REPO, "src/Main.java", 2);
        thread.setExercise(programmingExercise);
        commentThreadRepository.save(thread);

        var previousProgramming = buildProgrammingSnapshot(history.repositoryUri().toString(), history.oldCommit(), null, null, null);
        var currentProgramming = buildProgrammingSnapshot(history.repositoryUri().toString(), history.newCommit(), null, null, null);
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(3);
        assertThat(updated.isOutdated()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateThreadsForVersionChangeForAuxiliaryRepoLines() throws Exception {
        RepoHistory history = createRepoWithTwoCommits("aux-map");
        CommentThread thread = buildRepoThread(CommentThreadLocationType.AUXILIARY_REPO, "src/Main.java", 2);
        thread.setAuxiliaryRepositoryId(42L);
        thread.setExercise(programmingExercise);
        commentThreadRepository.save(thread);

        var previousAux = new ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO(42L, history.repositoryUri().toString(), history.oldCommit());
        var currentAux = new ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO(42L, history.repositoryUri().toString(), history.newCommit());
        var previousProgramming = buildProgrammingSnapshot(null, null, null, null, List.of(previousAux));
        var currentProgramming = buildProgrammingSnapshot(null, null, null, null, List.of(currentAux));
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(3);
        assertThat(updated.isOutdated()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSkipUpdateThreadsForVersionChangeWhenAuxRepoIdMissing() {
        CommentThread thread = buildRepoThread(CommentThreadLocationType.AUXILIARY_REPO, "src/Main.java", 5);
        thread.setExercise(programmingExercise);
        commentThreadRepository.save(thread);

        var previousProgramming = buildProgrammingSnapshot(null, null, null, null, null);
        var currentProgramming = buildProgrammingSnapshot(null, null, null, null, null);
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(5);
        assertThat(updated.isOutdated()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSkipUpdateThreadsForVersionChangeWhenAuxRepoCommitMissing() {
        CommentThread thread = buildRepoThread(CommentThreadLocationType.AUXILIARY_REPO, "src/Main.java", 5);
        thread.setAuxiliaryRepositoryId(42L);
        thread.setExercise(programmingExercise);
        commentThreadRepository.save(thread);

        var previousAux = new ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO(42L, "http://repo", "old");
        var currentAux = new ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO(42L, "http://repo", null);
        var previousProgramming = buildProgrammingSnapshot(null, null, null, null, List.of(previousAux));
        var currentProgramming = buildProgrammingSnapshot(null, null, null, null, List.of(currentAux));
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(5);
        assertThat(updated.isOutdated()).isFalse();
    }

    @Test
    void shouldReturnWhenUpdateThreadsForVersionChangeSnapshotsNull() {
        CommentThread thread = persistThread(programmingExercise);
        Integer originalLine = thread.getLineNumber();

        exerciseReviewService.updateThreadsForVersionChange(null, null);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(originalLine);
    }

    @Test
    void shouldReturnWhenUpdateThreadsForVersionChangeProgrammingDataMissing() {
        CommentThread thread = persistThread(programmingExercise);
        Integer originalLine = thread.getLineNumber();

        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), null);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), null);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        CommentThread updated = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(updated.getLineNumber()).isEqualTo(originalLine);
    }

    @Test
    void shouldReturnWhenUpdateThreadsForVersionChangeNoThreads() throws Exception {
        commentThreadRepository.deleteAll();

        RepoHistory history = createRepoWithTwoCommits("no-threads");
        var previousParticipation = new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, history.repositoryUri().toString(), null, history.oldCommit());
        var currentParticipation = new ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO(1L, history.repositoryUri().toString(), null, history.newCommit());
        var previousProgramming = buildProgrammingSnapshot(null, null, previousParticipation, null, null);
        var currentProgramming = buildProgrammingSnapshot(null, null, currentParticipation, null, null);
        ExerciseSnapshotDTO previous = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), previousProgramming);
        ExerciseSnapshotDTO current = buildExerciseSnapshot(programmingExercise.getId(), programmingExercise.getProblemStatement(), currentProgramming);

        exerciseReviewService.updateThreadsForVersionChange(previous, current);

        assertThat(commentThreadRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
    }

    private CommentThread buildThreadEntity() {
        CommentThread thread = new CommentThread();
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialFilePath(null);
        thread.setFilePath(null);
        thread.setInitialLineNumber(1);
        thread.setLineNumber(1);
        thread.setResolved(false);
        thread.setOutdated(false);
        return thread;
    }

    private CreateCommentThreadDTO buildThreadDto() {
        ensureExerciseVersion();
        return new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, null, null, 1, new UserCommentContentDTO("Initial"));
    }

    private void ensureExerciseVersion() {
        if (exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).isEmpty()) {
            exerciseVersionService.createExerciseVersion(programmingExercise);
        }
    }

    private CommentThread buildRepoThread(CommentThreadLocationType targetType, String filePath, int lineNumber) {
        CommentThread thread = new CommentThread();
        thread.setTargetType(targetType);
        thread.setInitialFilePath(filePath);
        thread.setFilePath(filePath);
        thread.setInitialLineNumber(lineNumber);
        thread.setLineNumber(lineNumber);
        thread.setResolved(false);
        thread.setOutdated(false);
        return thread;
    }

    private CommentThread persistThread(ProgrammingExercise exercise) {
        CommentThread thread = buildThreadEntity();
        thread.setExercise(exercise);
        return commentThreadRepository.save(thread);
    }

    private Comment buildUserCommentEntity(String text) {
        Comment comment = new Comment();
        comment.setType(CommentType.USER);
        comment.setContent(new UserCommentContentDTO(text));
        return comment;
    }

    private UserCommentContentDTO buildUserCommentContent(String text) {
        return new UserCommentContentDTO(text);
    }

    private ExerciseVersion createExerciseVersion() {
        exerciseVersionService.createExerciseVersion(programmingExercise);
        return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();
    }

    private ExerciseSnapshotDTO buildExerciseSnapshot(long exerciseId, String problemStatement, ProgrammingExerciseSnapshotDTO programmingData) {
        return new ExerciseSnapshotDTO(exerciseId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, problemStatement, null, null,
                null, null, null, null, null, null, programmingData, null, null, null, null);
    }

    private ProgrammingExerciseSnapshotDTO buildProgrammingSnapshot(String testRepositoryUri, String testsCommitId,
            ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO templateParticipation, ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO solutionParticipation,
            List<ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO> auxiliaryRepositories) {
        return new ProgrammingExerciseSnapshotDTO(testRepositoryUri, auxiliaryRepositories, null, null, null, null, null, null, null, null, null, null, templateParticipation,
                solutionParticipation, null, null, null, null, null, null, null, testsCommitId);
    }

    private RepoHistory createRepoWithTwoCommits(String suffix) throws Exception {
        LocalRepoWithGit repo = createLocalRepositoryWithGit(suffix);

        Path filePath = repo.workingCopyPath().resolve("src").resolve("Main.java");
        Files.createDirectories(filePath.getParent());

        String oldText = String.join("\n", "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "");
        FileUtils.writeStringToFile(filePath.toFile(), oldText, StandardCharsets.UTF_8);
        repo.git().add().addFilepattern(".").call();
        RevCommit oldCommit = GitService.commit(repo.git()).setMessage("Add file").call();
        repo.git().push().setRemote("origin").call();

        String newText = String.join("\n", "alpha", "inserted", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "");
        FileUtils.writeStringToFile(filePath.toFile(), newText, StandardCharsets.UTF_8);
        repo.git().add().addFilepattern(".").call();
        RevCommit newCommit = GitService.commit(repo.git()).setMessage("Update file").call();
        repo.git().push().setRemote("origin").call();

        return new RepoHistory(repo.uri(), oldCommit.getName(), newCommit.getName());
    }

    private LocalRepoWithGit createLocalRepositoryWithGit(String suffix) throws Exception {
        String repositorySlug = programmingExercise.getProjectKey().toLowerCase() + "-" + suffix;
        localVCService.createProjectForExercise(programmingExercise);
        localVCService.createRepository(programmingExercise.getProjectKey(), repositorySlug);
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(localVCBaseUri, programmingExercise.getProjectKey(), repositorySlug);
        Path workingCopyPath = tempFileUtilService.createTempDirectory("review-repo-" + suffix + "-");
        String localRepositoryUri = repositoryUri.getLocalRepositoryPath(localVCBasePath).toUri().toString();
        Git git = Git.cloneRepository().setURI(localRepositoryUri).setDirectory(workingCopyPath.toFile()).call();
        LocalRepoWithGit repo = new LocalRepoWithGit(repositoryUri, workingCopyPath, git);
        createdRepos.add(repo);
        return repo;
    }

    private record LocalRepoWithGit(LocalVCRepositoryUri uri, Path workingCopyPath, Git git) {
    }

    private record RepoHistory(LocalVCRepositoryUri repositoryUri, String oldCommit, String newCommit) {
    }
}
