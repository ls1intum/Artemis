package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewCommentService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewCommentService.LineMappingResult;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class ReviewCommentServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "reviewcommentservice";

    @Autowired
    private ExerciseReviewCommentService exerciseReviewCommentService;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private CommentThreadRepository commentThreadRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTest() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        programmingExercise.setProblemStatement("Line 1\nLine 2\nLine 3");
        programmingExerciseRepository.save(programmingExercise);
    }

    @AfterEach
    void tearDown() {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnThreadsWhenInstructorRequestsByExerciseId() {
        CommentThread thread = persistThread(programmingExercise);
        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise otherExercise = exerciseUtilService.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        persistThread(otherExercise);

        List<CommentThread> threads = exerciseReviewCommentService.findThreadsByExerciseId(programmingExercise.getId());

        assertThat(threads).hasSize(1);
        assertThat(threads.getFirst().getId()).isEqualTo(thread.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void shouldThrowForbiddenWhenStudentRequestsThreads() {
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> exerciseReviewCommentService.findThreadsByExerciseId(programmingExercise.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnThreadsWithCommentsWhenRequested() {
        CommentThread thread = persistThread(programmingExercise);
        Comment comment = buildUserComment("First");
        comment.setThread(thread);
        commentRepository.save(comment);

        List<CommentThread> threads = exerciseReviewCommentService.findThreadsWithCommentsByExerciseId(programmingExercise.getId());

        assertThat(threads).hasSize(1);
        CommentThread loaded = threads.getFirst();
        assertThat(loaded.getComments()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnCommentsOrderedByCreatedDate() {
        CommentThread thread = persistThread(programmingExercise);

        Comment first = buildUserComment("First");
        first.setThread(thread);
        first.setCreatedDate(Instant.now().minusSeconds(10));
        Comment second = buildUserComment("Second");
        second.setThread(thread);
        second.setCreatedDate(Instant.now());
        commentRepository.save(first);
        commentRepository.save(second);

        List<Comment> comments = exerciseReviewCommentService.findCommentsByThreadId(thread.getId());

        assertThat(comments).hasSize(2);
        assertThat(comments.getFirst().getContent()).isEqualTo(first.getContent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateThreadWhenValid() {
        CommentThread thread = buildThread();

        CommentThread saved = exerciseReviewCommentService.createThread(programmingExercise.getId(), thread);

        assertThat(saved.getExercise()).isNotNull();
        assertThat(saved.getExercise().getId()).isEqualTo(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadWithId() {
        CommentThread thread = buildThread();
        thread.setId(99L);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewCommentService.createThread(programmingExercise.getId(), thread));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldRejectThreadWithMismatchedExercise() {
        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise otherExercise = exerciseUtilService.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        CommentThread thread = buildThread();
        thread.setExercise(otherExercise);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> exerciseReviewCommentService.createThread(programmingExercise.getId(), thread));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateCommentWithAuthorAndInitialVersion() {
        ExerciseVersion initialVersion = createExerciseVersion();
        CommentThread thread = buildThread();
        thread.setInitialVersion(initialVersion);
        thread = exerciseReviewCommentService.createThread(programmingExercise.getId(), thread);

        Comment comment = buildUserComment("Initial");
        Comment saved = exerciseReviewCommentService.createComment(thread.getId(), comment);

        assertThat(saved.getAuthor()).isNotNull();
        assertThat(saved.getInitialVersion()).isEqualTo(initialVersion);
        assertThat(saved.getInitialCommitSha()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUseLatestVersionForReplyComments() {
        ExerciseVersion initialVersion = createExerciseVersion();
        CommentThread thread = buildThread();
        thread.setInitialVersion(initialVersion);
        thread = exerciseReviewCommentService.createThread(programmingExercise.getId(), thread);
        exerciseReviewCommentService.createComment(thread.getId(), buildUserComment("Initial"));

        programmingExercise.setProblemStatement("New Line\nLine 1\nLine 2\nLine 3");
        programmingExerciseRepository.save(programmingExercise);
        ExerciseVersion latestVersion = createExerciseVersion();

        Comment reply = exerciseReviewCommentService.createComment(thread.getId(), buildUserComment("Reply"));

        assertThat(reply.getInitialVersion()).isEqualTo(latestVersion);
        assertThat(reply.getInitialVersion()).isNotEqualTo(initialVersion);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldDeleteThreadWhenLastCommentRemoved() {
        CommentThread thread = exerciseReviewCommentService.createThread(programmingExercise.getId(), buildThread());
        Comment comment = exerciseReviewCommentService.createComment(thread.getId(), buildUserComment("Only"));

        exerciseReviewCommentService.deleteComment(comment.getId());

        assertThat(commentThreadRepository.findById(thread.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldKeepThreadWhenOtherCommentsExist() {
        CommentThread thread = exerciseReviewCommentService.createThread(programmingExercise.getId(), buildThread());
        Comment first = exerciseReviewCommentService.createComment(thread.getId(), buildUserComment("First"));
        exerciseReviewCommentService.createComment(thread.getId(), buildUserComment("Second"));

        exerciseReviewCommentService.deleteComment(first.getId());

        assertThat(commentThreadRepository.findById(thread.getId())).isPresent();
        assertThat(commentRepository.countByThreadId(thread.getId())).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateThreadResolvedState() {
        CommentThread thread = exerciseReviewCommentService.createThread(programmingExercise.getId(), buildThread());

        CommentThread updated = exerciseReviewCommentService.updateThreadResolvedState(thread.getId(), true);

        assertThat(updated.isResolved()).isTrue();
        CommentThread persisted = commentThreadRepository.findById(thread.getId()).orElseThrow();
        assertThat(persisted.isResolved()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateCommentContentAndTimestamp() {
        CommentThread thread = exerciseReviewCommentService.createThread(programmingExercise.getId(), buildThread());
        Comment comment = exerciseReviewCommentService.createComment(thread.getId(), buildUserComment("Initial"));
        Instant previousModified = comment.getLastModifiedDate();

        Comment updated = exerciseReviewCommentService.updateCommentContent(comment.getId(), new UserCommentContentDTO("Updated"));

        assertThat(updated.getContent()).isInstanceOf(UserCommentContentDTO.class);
        assertThat(((UserCommentContentDTO) updated.getContent()).text()).isEqualTo("Updated");
        assertThat(updated.getLastModifiedDate()).isAfterOrEqualTo(previousModified);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNullCommitForProblemStatementThreads() {
        String commitSha = exerciseReviewCommentService.resolveLatestCommitSha(CommentThreadLocationType.PROBLEM_STATEMENT, null, programmingExercise.getId());

        assertThat(commitSha).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnLatestCommitForTemplateRepo() throws Exception {
        LocalRepoWithUri repo = createLocalRepository("template");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        var templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(repositoryUri.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();

        String commitSha = exerciseReviewCommentService.resolveLatestCommitSha(CommentThreadLocationType.TEMPLATE_REPO, null, programmingExercise.getId());

        assertThat(commitSha).isEqualTo(gitService.getLastCommitHash(repositoryUri));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnLatestCommitForAuxiliaryRepo() throws Exception {
        LocalRepoWithUri repo = createLocalRepository("aux");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName("aux");
        auxiliaryRepository.setCheckoutDirectory("aux");
        auxiliaryRepository.setRepositoryUri(repositoryUri.toString());
        auxiliaryRepository.setExercise(programmingExercise);
        programmingExercise.getAuxiliaryRepositories().add(auxiliaryRepository);
        programmingExerciseRepository.save(programmingExercise);
        auxiliaryRepository = programmingExerciseRepository.findWithAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow().getAuxiliaryRepositories().getFirst();

        String commitSha = exerciseReviewCommentService.resolveLatestCommitSha(CommentThreadLocationType.AUXILIARY_REPO, auxiliaryRepository.getId(), programmingExercise.getId());

        assertThat(commitSha).isEqualTo(gitService.getLastCommitHash(repositoryUri));
    }

    @Test
    void mapLineInText_shiftsLinesAfterInsertion() {
        String oldText = "a\nb\nc\nd\n";
        String newText = "a\nb\nx\nc\nd\n";

        LineMappingResult result = exerciseReviewCommentService.mapLineInText(oldText, newText, 3);

        assertThat(result.newLine()).isEqualTo(4);
        assertThat(result.outdated()).isFalse();
    }

    @Test
    void mapLineInText_marksLineOutdatedOnReplacement() {
        String oldText = "a\nb\nc\nd\n";
        String newText = "a\nb\nc2\nd\n";

        LineMappingResult result = exerciseReviewCommentService.mapLineInText(oldText, newText, 3);

        assertThat(result.newLine()).isEqualTo(3);
        assertThat(result.outdated()).isTrue();
    }

    @Test
    void mapLine_mapsLinesBetweenCommitsInRepository() throws Exception {
        LocalRepoWithUri repo = createLocalRepository("linemap");
        LocalVCRepositoryUri repositoryUri = repo.uri();
        LocalRepository repository = repo.repository();

        Path filePath = repository.workingCopyGitRepoFile.toPath().resolve("src").resolve("Main.java");
        Files.createDirectories(filePath.getParent());

        String oldText = String.join("\n", "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "");
        FileUtils.writeStringToFile(filePath.toFile(), oldText, StandardCharsets.UTF_8);
        repository.workingCopyGitRepo.add().addFilepattern(".").call();
        RevCommit oldCommit = GitService.commit(repository.workingCopyGitRepo).setMessage("Add file").call();
        repository.workingCopyGitRepo.push().setRemote("origin").call();

        String newText = String.join("\n", "alpha", "inserted", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta-updated", "iota", "kappa", "");
        FileUtils.writeStringToFile(filePath.toFile(), newText, StandardCharsets.UTF_8);
        repository.workingCopyGitRepo.add().addFilepattern(".").call();
        RevCommit newCommit = GitService.commit(repository.workingCopyGitRepo).setMessage("Update file").call();
        repository.workingCopyGitRepo.push().setRemote("origin").call();

        LineMappingResult shiftedLine = exerciseReviewCommentService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 2);
        assertThat(shiftedLine.newLine()).isEqualTo(3);
        assertThat(shiftedLine.outdated()).isFalse();

        LineMappingResult editedLine = exerciseReviewCommentService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 8);
        assertThat(editedLine.newLine()).isEqualTo(9);
        assertThat(editedLine.outdated()).isTrue();

        LineMappingResult unchangedLine = exerciseReviewCommentService.mapLine(repositoryUri, "src/Main.java", oldCommit.getName(), newCommit.getName(), 9);
        assertThat(unchangedLine.newLine()).isEqualTo(10);
        assertThat(unchangedLine.outdated()).isFalse();
    }

    private CommentThread buildThread() {
        CommentThread thread = new CommentThread();
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialFilePath("problem_statement.md");
        thread.setFilePath("problem_statement.md");
        thread.setInitialLineNumber(1);
        thread.setLineNumber(1);
        thread.setResolved(false);
        thread.setOutdated(false);
        return thread;
    }

    private CommentThread persistThread(ProgrammingExercise exercise) {
        CommentThread thread = buildThread();
        thread.setExercise(exercise);
        return commentThreadRepository.save(thread);
    }

    private Comment buildUserComment(String text) {
        Comment comment = new Comment();
        comment.setType(CommentType.USER);
        comment.setContent(new UserCommentContentDTO(text));
        return comment;
    }

    private ExerciseVersion createExerciseVersion() {
        exerciseVersionService.createExerciseVersion(programmingExercise);
        return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();
    }

    private LocalRepoWithUri createLocalRepository(String suffix) throws Exception {
        String repositorySlug = programmingExercise.getProjectKey().toLowerCase() + "-" + suffix;
        LocalRepository repository = RepositoryExportTestUtil
                .trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(programmingExercise.getProjectKey(), repositorySlug));
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, programmingExercise.getProjectKey(), repositorySlug));
        return new LocalRepoWithUri(repository, repositoryUri);
    }

    private record LocalRepoWithUri(LocalRepository repository, LocalVCRepositoryUri uri) {
    }
}
