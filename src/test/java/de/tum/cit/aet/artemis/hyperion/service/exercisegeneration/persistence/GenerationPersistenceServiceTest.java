package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOutcome;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationScheduleService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class GenerationPersistenceServiceTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    @TempDir
    private Path temporaryDirectory;

    private GitService gitService;

    private RepositoryService repositoryService;

    private ProgrammingExerciseParticipationService participationService;

    private ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private ProgrammingSubmissionService programmingSubmissionService;

    private ExerciseVersionService exerciseVersionService;

    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    private ResultTestRepository resultRepository;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    private ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    private TempFileUtilService tempFileUtilService;

    private GenerationPersistenceService service;

    private ProgrammingExercise exercise;

    private AtomicReference<String> exerciseProblemStatement;

    private AtomicReference<String> exerciseTitle;

    private LocalVCRepositoryUri templateUri;

    private LocalVCRepositoryUri solutionUri;

    private LocalVCRepositoryUri testsUri;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        gitService = mock(GitService.class);
        repositoryService = mock(RepositoryService.class);
        participationService = mock(ProgrammingExerciseParticipationService.class);
        continuousIntegrationTriggerService = mock(ContinuousIntegrationTriggerService.class);
        programmingSubmissionService = mock(ProgrammingSubmissionService.class);
        exerciseVersionService = mock(ExerciseVersionService.class);
        testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        resultRepository = mock(ResultTestRepository.class);
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        programmingExerciseTaskService = mock(ProgrammingExerciseTaskService.class);
        programmingExerciseCreationScheduleService = mock(ProgrammingExerciseCreationScheduleService.class);
        tempFileUtilService = new TempFileUtilService(Path.of("build/tmp/hyperion-persistence-test"));
        ProgrammingExerciseTestCase behaviourCase = mock(ProgrammingExerciseTestCase.class);
        when(behaviourCase.getTestName()).thenReturn("behaviourTest");
        when(testCaseRepository.findByExerciseId(anyLong())).thenReturn(Set.of(behaviourCase));
        when(resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(anyLong())).thenReturn(Optional.empty(), Optional.of(resultWithId(1L)));
        when(programmingSubmissionService.existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), anyString(), any())).thenReturn(true);
        when(programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any())).thenReturn(1);
        service = new GenerationPersistenceService("main", gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService,
                exerciseVersionService, testCaseRepository, resultRepository, programmingExerciseRepository, programmingExerciseTaskService, tempFileUtilService,
                programmingExerciseCreationScheduleService, Duration.ofSeconds(2), Duration.ofMillis(5));

        exercise = mock(ProgrammingExercise.class);
        exerciseProblemStatement = new AtomicReference<>();
        exerciseTitle = new AtomicReference<>();
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getProblemStatement()).thenAnswer(invocation -> exerciseProblemStatement.get());
        when(exercise.getTitle()).thenAnswer(invocation -> exerciseTitle.get());
        doAnswer(invocation -> {
            exerciseProblemStatement.set(invocation.getArgument(0));
            return null;
        }).when(exercise).setProblemStatement(any());
        doAnswer(invocation -> {
            exerciseTitle.set(invocation.getArgument(0));
            return null;
        }).when(exercise).setTitle(any());
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(solutionParticipation.getId()).thenReturn(1L);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        templateUri = mock(LocalVCRepositoryUri.class);
        solutionUri = mock(LocalVCRepositoryUri.class);
        testsUri = mock(LocalVCRepositoryUri.class);
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(templateUri);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);
        when(exercise.getRepositoryURI(RepositoryType.TESTS)).thenReturn(testsUri);
    }

    private static Result resultWithId(long id) {
        Result result = new Result();
        result.setId(id);
        return result;
    }

    private GenerationOutcome outcomeWith(Map<String, String> template, Map<String, String> solution, Map<String, String> tests, String problemStatement) {
        return outcomeWithPlan(template, solution, tests, problemStatement, null);
    }

    private GenerationOutcome outcomeWithPlan(Map<String, String> template, Map<String, String> solution, Map<String, String> tests, String problemStatement, String testPlanJson) {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.isMechanicallyVerified()).thenReturn(true);
        when(outcome.producedFiles(RepositoryType.TEMPLATE)).thenReturn(template);
        when(outcome.producedFiles(RepositoryType.SOLUTION)).thenReturn(solution);
        when(outcome.producedFiles(RepositoryType.TESTS)).thenReturn(tests);
        when(outcome.producedProblemStatement()).thenReturn(problemStatement);
        when(outcome.testPlanJson()).thenReturn(testPlanJson);
        when(outcome.seedRepositoryHeads()).thenReturn(Map.of());
        return outcome;
    }

    @Test
    void persistRejectsCandidateThatDidNotPassMechanicalVerification() throws Exception {
        GenerationOutcome outcome = mock(GenerationOutcome.class);

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mechanical verification");
        verify(gitService, never()).getOrCheckoutRepository(any(), any(), any(), any(Boolean.class), any(), any(Boolean.class));
    }

    @Test
    void persistRejectsSupportedSecretFileBeforeAnyDurableWrite() throws Exception {
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("src/Fixture.java", GITHUB_SENTINEL), Map.of("Test.java", "x"), "safe statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome))
                .isInstanceOf(de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy.SecretMaterialException.class).hasMessageContaining("GITHUB_TOKEN")
                .hasMessageNotContaining(GITHUB_SENTINEL);
        verify(repositoryService, never()).createFile(any(), any(), any());
        verify(repositoryService, never()).deleteFile(any(), any());
        verify(gitService, never()).getOrCheckoutRepository(any(), any(), any(), any(Boolean.class), any(), any(Boolean.class));
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persistRejectsSupportedSecretProblemStatementBeforeAnyDurableWrite() throws Exception {
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), GITHUB_SENTINEL);

        assertThatThrownBy(() -> service.persist(exercise, user, outcome))
                .isInstanceOf(de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy.SecretMaterialException.class).hasMessageContaining("GITHUB_TOKEN")
                .hasMessageNotContaining(GITHUB_SENTINEL);
        verify(repositoryService, never()).createFile(any(), any(), any());
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    private Repository repository;

    private void stubRepositoryWorkingTree(Repository repository) throws Exception {
        Path repositoryDirectory = temporaryDirectory.resolve("repository");
        FileUtils.forceMkdir(repositoryDirectory.toFile());
        when(repository.getLocalPath()).thenReturn(repositoryDirectory);
    }

    private void stubSuccessfulCheckoutAndCommits() throws Exception {
        repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq("main"), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("pre-template", "pre-solution", "pre-tests");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("hash-template", "hash-solution", "hash-tests");
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("hash-template");
        when(gitService.getLastCommitHash(solutionUri, "main")).thenReturn("hash-solution");
        when(gitService.getLastCommitHash(testsUri, "main")).thenReturn("hash-tests");
    }

    @Test
    void persist_happyPath_commitsInProductionOrderWaitsForTestsPushBuildAndCreatesVersion() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");
        exerciseProblemStatement.set("old statement");

        GenerationPersistenceService.PersistResult persistResult = service.persist(exercise, user, outcome, "old statement", null, "job-1", () -> true);

        InOrder order = Mockito.inOrder(gitService, resultRepository, programmingSubmissionService, exerciseVersionService);
        order.verify(gitService).getLocalHeadHash(repository);
        order.verify(gitService).commitStagedChanges(repository, "Generate exercise with Hyperion (job-1)", user);
        order.verify(gitService).pushCommitWithLease(repository, "hash-template", "main", "pre-template");
        order.verify(gitService).getLocalHeadHash(repository);
        order.verify(gitService).commitStagedChanges(repository, "Generate exercise with Hyperion (job-1)", user);
        order.verify(gitService).pushCommitWithLease(repository, "hash-solution", "main", "pre-solution");
        order.verify(resultRepository).findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(anyLong());
        order.verify(gitService).getLocalHeadHash(repository);
        order.verify(gitService).commitStagedChanges(repository, "Generate exercise with Hyperion (job-1)", user);
        order.verify(gitService).pushCommitWithLease(repository, "hash-tests", "main", "pre-tests");
        order.verify(programmingSubmissionService).existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any());
        order.verify(exerciseVersionService).createExerciseVersionOrThrow(exercise, user,
                Map.of(RepositoryType.TEMPLATE, "hash-template", RepositoryType.SOLUTION, "hash-solution", RepositoryType.TESTS, "hash-tests"));
        verify(programmingSubmissionService).createSolutionParticipationSubmissionWithTypeTest(1L, "hash-tests");
        verify(continuousIntegrationTriggerService).triggerRestrictedBuild(solutionParticipation, "hash-tests", RepositoryType.TESTS);
        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "new statement", null, "old statement", null);

        assertThat(persistResult.prePersistHeads()).containsEntry(RepositoryType.TEMPLATE, "pre-template").containsEntry(RepositoryType.SOLUTION, "pre-solution")
                .containsEntry(RepositoryType.TESTS, "pre-tests");
        assertThat(persistResult.postPersistHeads()).containsEntry(RepositoryType.TEMPLATE, "hash-template").containsEntry(RepositoryType.SOLUTION, "hash-solution")
                .containsEntry(RepositoryType.TESTS, "hash-tests");
        assertThat(persistResult.persistedProblemStatement()).isEqualTo("new statement");
        assertThat(persistResult.persistedTitle()).isNull();
    }

    @Test
    void persist_whenBaselineInvalidationFails_startsNoDurableWrite() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.getLocalHeadHash(repository)).thenReturn("pre-template", "hash-template");
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("pre-template");
        exerciseProblemStatement.set("old statement");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of(), Map.of(), "new statement");
        AtomicInteger invalidationAttempts = new AtomicInteger();

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "old statement", null, "job-1", GenerationMode.GENERATE, () -> true, () -> {
            invalidationAttempts.incrementAndGet();
            throw new IllegalStateException("Hazelcast invalidation failed");
        })).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("Hazelcast invalidation failed").satisfies(exception -> {
            GenerationIncompleteException incomplete = (GenerationIncompleteException) exception;
            assertThat(incomplete.liveExerciseChanged()).isFalse();
        });

        verify(gitService, never()).pushCommitWithLease(any(), any(), any(), any());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any(), anyMap());
        assertThat(invalidationAttempts).hasValue(1);
    }

    @Test
    void persist_whenCandidateIsANoOp_preservesTheExistingBaseline() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.isWorkingCopyClean(repository)).thenReturn(true);
        AtomicInteger invalidationAttempts = new AtomicInteger();

        service.persist(exercise, user, outcomeWith(Map.of("Template.java", "unchanged"), Map.of(), Map.of(), ""), null, null, "job-1", GenerationMode.GENERATE, () -> true,
                invalidationAttempts::incrementAndGet);

        assertThat(invalidationAttempts).hasValue(0);
        verify(gitService, never()).pushCommitWithLease(any(), any(), any(), any());
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
    }

    @Test
    void persist_whenDraftEligibilityIsLostBeforePush_doesNotPublishTheCommit() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.getLocalHeadHash(repository)).thenReturn("pre-template", "hash-template");
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("pre-template");
        AtomicBoolean eligible = new AtomicBoolean(true);
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenAnswer(invocation -> {
            eligible.set(false);
            return "hash-template";
        });

        assertThatThrownBy(() -> service.persist(exercise, user, outcomeWith(Map.of("Template.java", "t"), Map.of(), Map.of(), ""), null, null, "job-1", GenerationMode.GENERATE,
                eligible::get)).isInstanceOf(GenerationIncompleteException.class).satisfies(exception -> {
                    GenerationIncompleteException incomplete = (GenerationIncompleteException) exception;
                    assertThat(incomplete.liveExerciseChanged()).isFalse();
                });

        verify(gitService, never()).pushCommitWithLease(any(), any(), any(), any());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
    }

    @Test
    void persist_whenDraftEligibilityIsLostDuringPush_compensatesThePublishedCommit() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.getLocalHeadHash(repository)).thenReturn("pre-template", "hash-template");
        AtomicBoolean eligible = new AtomicBoolean(true);
        doAnswer(invocation -> {
            eligible.set(false);
            return null;
        }).when(gitService).pushCommitWithLease(repository, "hash-template", "main", "pre-template");

        assertThatThrownBy(() -> service.persist(exercise, user, outcomeWith(Map.of("Template.java", "t"), Map.of(), Map.of(), ""), null, null, "job-1", GenerationMode.GENERATE,
                eligible::get)).isInstanceOf(GenerationIncompleteException.class).satisfies(exception -> {
                    GenerationIncompleteException incomplete = (GenerationIncompleteException) exception;
                    assertThat(incomplete.liveExerciseChanged()).isFalse();
                });

        verify(gitService).pushCommitWithLease(repository, "hash-template", "main", "pre-template");
        verify(gitService).resetToCommitAndForcePush(repository, "pre-template", "hash-template", "main");
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any(), anyMap());
    }

    @Test
    void persist_usesExerciseRepositoryBranch() throws Exception {
        String exerciseBranch = "release";
        ProgrammingExerciseBuildConfig buildConfig = mock(ProgrammingExerciseBuildConfig.class);
        when(buildConfig.getBranch()).thenReturn(exerciseBranch);
        when(exercise.getBuildConfig()).thenReturn(buildConfig);
        repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq(exerciseBranch), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("pre-template", "pre-solution", "pre-tests");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("hash-template", "hash-solution", "hash-tests");
        when(gitService.getLastCommitHash(templateUri, exerciseBranch)).thenReturn("hash-template");
        when(gitService.getLastCommitHash(solutionUri, exerciseBranch)).thenReturn("hash-solution");
        when(gitService.getLastCommitHash(testsUri, exerciseBranch)).thenReturn("hash-tests");
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        GenerationPersistenceService.PersistResult result = service.persist(exercise, user,
                outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), ""));

        assertThat(result.repositoryBranch()).isEqualTo(exerciseBranch);
        verify(gitService, times(3)).getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq(exerciseBranch),
                eq(false));
        verify(gitService).pushCommitWithLease(repository, "hash-template", exerciseBranch, "pre-template");
        verify(gitService).pushCommitWithLease(repository, "hash-solution", exerciseBranch, "pre-solution");
        verify(gitService).pushCommitWithLease(repository, "hash-tests", exerciseBranch, "pre-tests");
    }

    @Test
    void persist_omitsUnchangedRepositoriesFromTheRevertHeads() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.isWorkingCopyClean(repository)).thenReturn(true, false, false);
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("pre-template");
        when(gitService.getLastCommitHash(solutionUri, "main")).thenReturn("hash-template");
        when(gitService.getLastCommitHash(testsUri, "main")).thenReturn("hash-solution");
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        GenerationPersistenceService.PersistResult result = service.persist(exercise, user, outcome);

        assertThat(result.prePersistHeads()).doesNotContainKey(RepositoryType.TEMPLATE).containsEntry(RepositoryType.SOLUTION, "pre-solution").containsEntry(RepositoryType.TESTS,
                "pre-tests");
        assertThat(result.postPersistHeads()).doesNotContainKey(RepositoryType.TEMPLATE);
    }

    @Test
    void persist_whenRepositoryChangedAfterVerification_refusesToOverwriteStaleSeed() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.getLocalHeadHash(repository)).thenReturn("changed-template");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of(), Map.of(), "");
        when(outcome.seedRepositoryHeads()).thenReturn(Map.of(RepositoryType.TEMPLATE, "seed-template"));

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class)
                .hasMessageContaining("changed after Hyperion verified");

        verify(repositoryService, never()).commitChanges(any(), any());
    }

    @Test
    void persist_detectsRepositoryDivergenceBeforeMetadataFinalizationWithoutOverwritingIt() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("concurrent-instructor-head");
        when(gitService.getLastCommitHash(solutionUri, "main")).thenReturn("hash-solution");
        when(gitService.getLastCommitHash(testsUri, "main")).thenReturn("hash-tests");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("stale repository set")
                .hasMessageContaining("manual review");

        verify(gitService).resetToCommitAndForcePush(repository, "pre-tests", "hash-tests", "main");
        verify(gitService).resetToCommitAndForcePush(repository, "pre-solution", "hash-solution", "main");
        verify(gitService, never()).resetToCommitAndForcePush(any(), eq("pre-template"), any(), any());
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
    }

    @Test
    void persist_rechecksRepositoryHeadsAfterTestCaseSyncBeforeCreatingVersion() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("hash-template", "hash-template", "concurrent-instructor-head");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("stale repository set")
                .hasMessageContaining("instructor review");

        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(exercise, user);
        verify(gitService, never()).resetToCommitAndForcePush(any(), eq("pre-template"), any(), any());
    }

    @Test
    void persist_whenExerciseVersionCreationFails_keepsVerifiedRepositoryCommitsForInstructorReview() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        Mockito.doThrow(new IllegalStateException("version store down")).when(exerciseVersionService).createExerciseVersionOrThrow(eq(exercise), eq(user), anyMap());

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("exercise version")
                .hasMessageContaining("INCOMPLETE");

        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
    }

    @Test
    void persist_whenTestsBuildBaselineCannotBeCaptured_compensatesBeforePushingTests() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenThrow(new IllegalStateException("build state unavailable"));

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("INCOMPLETE")
                .hasMessageContaining("build state unavailable");

        InOrder order = Mockito.inOrder(gitService, exerciseVersionService);
        order.verify(gitService).resetToCommitAndForcePush(repository, "pre-solution", "hash-solution", "main");
        order.verify(gitService).resetToCommitAndForcePush(repository, "pre-template", "hash-template", "main");
        verify(gitService, never()).pushCommitWithLease(repository, "hash-tests", "main", "pre-tests");
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(exercise, user);
    }

    @Test
    void persist_keepsVerifiedRepositoryCommits_whenProblemStatementUpdateFails() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        exerciseProblemStatement.set("old statement");
        when(programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(1L, "new statement", null, "old statement", null)).thenReturn(0);

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("problem statement")
                .hasMessageContaining("INCOMPLETE");

        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(programmingSubmissionService, never()).existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), anyString(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(exercise, user);
    }

    @Test
    void persist_usesJobStartMetadataAsProblemStatementGuard() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        exerciseProblemStatement.set("manual edit while generation was running");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "original statement", null)).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(GenerationIncompleteException.class).hasMessageContaining("problem statement");

        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(repositoryService, never()).commitChanges(any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persist_refusesMetadataUpdateBeforeRepositoryCommits_whenExerciseRowIsMissing() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.empty());
        exerciseProblemStatement.set("old statement");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "old statement", null)).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(GenerationIncompleteException.class).hasMessageContaining("problem statement");

        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(repositoryService, never()).commitChanges(any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persist_allowsLineEndingAndOuterWhitespaceMetadataDriftFromExerciseSetup() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        exerciseProblemStatement.set(" old\r\nstatement\n");
        exerciseTitle.set("Original title");
        when(programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(1L, "new statement", "Original title", " old\r\nstatement\n", "Original title")).thenReturn(1);

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        service.persist(exercise, user, outcome, "old\nstatement", "Original title");

    }

    @Test
    void persist_allowsTaskReferenceIdNormalizationFromInitialTestCaseSync() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        exerciseProblemStatement.set("[task][Sort](<testid>7</testid>)");
        exerciseTitle.set("Original title");
        doAnswer(invocation -> {
            ProgrammingExercise candidate = invocation.getArgument(0);
            candidate.setProblemStatement(candidate.getProblemStatement().replace("<testid>7</testid>", "testBubbleSort"));
            return null;
        }).when(programmingExerciseTaskService).replaceTestIdsWithNames(any());
        when(programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(1L, "new statement", "Original title", "[task][Sort](<testid>7</testid>)", "Original title"))
                .thenReturn(1);

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        service.persist(exercise, user, outcome, "[task][Sort](testBubbleSort)", "Original title");

    }

    @Test
    void persist_refusesMeaningfulMarkdownWhitespaceMetadataEditBeforeRepositoryCommits() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        exerciseProblemStatement.set("old    statement");
        exerciseTitle.set("Original title");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "old statement", "Original title")).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(GenerationIncompleteException.class).hasMessageContaining("problem statement/title changed");

        verify(repositoryService, never()).commitChanges(any(), any());
    }

    @Test
    void persist_refusesTitleEditEvenWhenProblemStatementOutputIsUnchangedBeforeRepositoryCommits() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        exerciseProblemStatement.set("old statement");
        exerciseTitle.set("Edited title");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "old statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "old statement", "Original title")).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(GenerationIncompleteException.class).hasMessageContaining("problem statement/title changed");

        verify(repositoryService, never()).commitChanges(any(), any());
    }

    @Test
    void persist_refusesMixedExpectedAndTargetMetadataDuringProblemStatementSave() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        exerciseProblemStatement.set("");
        exerciseTitle.set("Brief title");

        ProgrammingExercise currentAtStart = new ProgrammingExercise();
        currentAtStart.setProblemStatement("");
        currentAtStart.setTitle("Brief title");
        ProgrammingExercise mixedManualEdit = new ProgrammingExercise();
        mixedManualEdit.setProblemStatement("# Generated title\n\nnew statement");
        mixedManualEdit.setTitle("Brief title");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentAtStart), Optional.of(mixedManualEdit));

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "# Generated title\n\nnew statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "", "Brief title")).isInstanceOf(GenerationIncompleteException.class)
                .hasMessageContaining("problem statement/title changed");

        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(programmingSubmissionService, never()).existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), anyString(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(exercise, user);
    }

    @Test
    void persist_refusesMetadataEditAfterRepositoryCommitsBeforeVersionWhenProblemStatementOutputIsUnchanged() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);
        exerciseProblemStatement.set("old statement");
        exerciseTitle.set("Old title");

        ProgrammingExercise currentAtStart = new ProgrammingExercise();
        currentAtStart.setProblemStatement("old statement");
        currentAtStart.setTitle("Old title");
        ProgrammingExercise manualEditBeforeVersion = new ProgrammingExercise();
        manualEditBeforeVersion.setProblemStatement("old statement");
        manualEditBeforeVersion.setTitle("Manual edit");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentAtStart), Optional.of(manualEditBeforeVersion));

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "old statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome, "old statement", "Old title")).isInstanceOf(GenerationIncompleteException.class)
                .hasMessageContaining("problem statement/title changed").hasMessageContaining("INCOMPLETE");

        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
    }

    @Test
    void persist_keepsSavedProblemStatementAndRepositories_whenTaskExtractionFails() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        exerciseProblemStatement.set("old statement");
        ProgrammingExercise currentBeforeSave = new ProgrammingExercise();
        currentBeforeSave.setProblemStatement("old statement");
        ProgrammingExercise currentAfterSave = new ProgrammingExercise();
        currentAfterSave.setProblemStatement("new statement");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentBeforeSave), Optional.of(currentBeforeSave), Optional.of(currentAfterSave));
        Mockito.doThrow(new IllegalStateException("task extraction failed")).doNothing().when(programmingExerciseTaskService).updateTasksFromProblemStatement(exercise);

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("task extraction failed")
                .hasMessageContaining("INCOMPLETE");

        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "new statement", null, "old statement", null);
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(1L, "old statement", null, "new statement", null);
        verify(programmingSubmissionService, never()).existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), anyString(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(exercise, user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persist_zeroWeightsBuildGateTestCases_setsTheRealWeightToZeroAndPersistsOnlyThoseCases() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        ProgrammingExerciseTestCase buildGate = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.CompileSort").weight(1.0);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("sort-test.push_then_pop").weight(1.0);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(buildGate, behaviour));

        service.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), ""));

        assertThat(buildGate.getWeight()).as("build gate zero-weighted").isEqualTo(0.0);
        assertThat(behaviour.getWeight()).as("behaviour test left graded").isEqualTo(1.0);
        ArgumentCaptor<Iterable<ProgrammingExerciseTestCase>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(testCaseRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).containsExactly(buildGate);
    }

    @Test
    void persist_waitsForTheCompleteSyncBeforeZeroWeighting_soNoGateFromTheFullSetIsMissed() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        ProgrammingExerciseTestCase configure = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.TestConfigure").weight(1.0);
        ProgrammingExerciseTestCase compileSort = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.CompileSort").weight(1.0);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("sort-test.push_then_pop").weight(1.0);
        AtomicInteger matchingResultPolls = new AtomicInteger();
        when(programmingSubmissionService.existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any()))
                .thenAnswer(invocation -> matchingResultPolls.incrementAndGet() >= 3);
        when(testCaseRepository.findByExerciseId(1L)).thenAnswer(invocation -> matchingResultPolls.get() >= 3 ? Set.of(configure, compileSort, behaviour) : Set.of(configure));

        service.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), ""));

        assertThat(configure.getWeight()).as("configure gate (present in the partial set) zero-weighted").isEqualTo(0.0);
        assertThat(compileSort.getWeight()).as("compile gate (only in the complete set) zero-weighted").isEqualTo(0.0);
        assertThat(behaviour.getWeight()).as("behaviour test left graded").isEqualTo(1.0);
    }

    @Test
    void persist_appliesHiddenPlanAndSchedulesCanonicalDueDateRecalculation() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        when(exercise.getDueDate()).thenReturn(ZonedDateTime.now().plusDays(1));
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("behaviourTest").weight(1.0).visibility(Visibility.ALWAYS);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(behaviour));
        String plan = "{\"tests\":[{\"name\":\"behaviourTest\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";

        service.persist(exercise, user, outcomeWithPlan(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), "", plan));

        assertThat(behaviour.getWeight()).isEqualTo(3.0);
        assertThat(behaviour.getVisibility()).isEqualTo(Visibility.AFTER_DUE_DATE);
        verify(programmingExerciseCreationScheduleService).scheduleOperations(1L);
    }

    @Test
    void persistKeepsServerSeededStructuralChecksVisibleAndZeroWeightWithoutAgentPlanEntries() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("behaviourTest").weight(1.0).visibility(Visibility.ALWAYS);
        ProgrammingExerciseTestCase structural = new ProgrammingExerciseTestCase().testName("testClass[Strategy]").weight(1.0).visibility(Visibility.AFTER_DUE_DATE);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(behaviour, structural));
        String plan = "{\"tests\":[{\"name\":\"behaviourTest\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

        service.persist(exercise, user, outcomeWithPlan(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), "", plan));

        assertThat(behaviour.getWeight()).isEqualTo(3.0);
        assertThat(structural.getWeight()).isZero();
        assertThat(structural.getVisibility()).isEqualTo(Visibility.ALWAYS);
    }

    @Test
    void persist_appliesVerifiedPlanEvenWhenTheTestsRepositoryHasNoNewCommit() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("hash-template", "hash-solution", null);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("behaviourTest").weight(1.0).visibility(Visibility.ALWAYS);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(behaviour));
        String plan = "{\"tests\":[{\"name\":\"behaviourTest\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

        service.persist(exercise, user, outcomeWithPlan(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), "", plan));

        assertThat(behaviour.getWeight()).isEqualTo(3.0);
        verify(continuousIntegrationTriggerService, never()).triggerRestrictedBuild(any(), anyString(), any());
        verify(testCaseRepository).saveAll(any());
    }

    @Test
    void persist_failsFinalizationRatherThanPublishingAHiddenPlanWithoutADueDate() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("behaviourTest").weight(1.0).visibility(Visibility.ALWAYS);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(behaviour));
        String plan = "{\"tests\":[{\"name\":\"behaviourTest\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";

        assertThatThrownBy(() -> service.persist(exercise, user, outcomeWithPlan(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), "", plan)))
                .isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("INCOMPLETE").hasMessageContaining("has no due date");

        assertThat(behaviour.getWeight()).isEqualTo(1.0);
        assertThat(behaviour.getVisibility()).isEqualTo(Visibility.ALWAYS);
        verify(programmingExerciseCreationScheduleService, never()).scheduleOperations(anyLong());
    }

    @Test
    void persist_failsFinalizationWhenSynchronizedTestsDoNotMatchTheVerifiedPlan() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        ProgrammingExerciseTestCase renamed = new ProgrammingExerciseTestCase().testName("renamedTest").weight(1.0).visibility(Visibility.ALWAYS);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(renamed));
        String plan = "{\"tests\":[{\"name\":\"verifiedTest\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

        assertThatThrownBy(() -> service.persist(exercise, user, outcomeWithPlan(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), "", plan)))
                .isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("INCOMPLETE").hasMessageContaining("Missing saved tests: [verifiedTest]")
                .hasMessageContaining("unplanned saved tests: [renamedTest]");

        assertThat(renamed.getWeight()).isEqualTo(1.0);
        verify(testCaseRepository, never()).saveAll(any());
    }

    @Test
    void persist_waitsForTheSpecificTriggeredTestsCommitHash_notAnyNewerSolutionResult() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        ProgrammingExerciseTestCase configure = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.TestConfigure").weight(1.0);
        ProgrammingExerciseTestCase compileSort = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.CompileSort").weight(1.0);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("sort-test.push_then_pop").weight(1.0);
        when(resultRepository.findFirstBySubmissionParticipationIdOrderByCompletionDateDesc(anyLong())).thenReturn(Optional.of(resultWithId(5L)), Optional.of(resultWithId(6L)),
                Optional.of(resultWithId(6L)), Optional.of(resultWithId(7L)));
        AtomicInteger matchingResultPolls = new AtomicInteger();
        when(programmingSubmissionService.existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any()))
                .thenAnswer(invocation -> matchingResultPolls.incrementAndGet() >= 3);
        when(testCaseRepository.findByExerciseId(1L)).thenAnswer(invocation -> matchingResultPolls.get() >= 3 ? Set.of(configure, compileSort, behaviour) : Set.of(configure));

        service.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), ""));

        assertThat(configure.getWeight()).as("configure gate zero-weighted").isEqualTo(0.0);
        assertThat(compileSort.getWeight()).as("compile gate from the triggered TEST commit zero-weighted").isEqualTo(0.0);
        assertThat(behaviour.getWeight()).as("behaviour test left graded").isEqualTo(1.0);
        verify(programmingSubmissionService, atLeastOnce()).existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any());
    }

    @Test
    void persist_sameCountReSync_returnsAsSoonAsTheBuildResultLands_insteadOfSpinning() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        GenerationPersistenceService promptService = new GenerationPersistenceService("main", gitService, repositoryService, participationService,
                continuousIntegrationTriggerService, programmingSubmissionService, exerciseVersionService, testCaseRepository, resultRepository, programmingExerciseRepository,
                programmingExerciseTaskService, tempFileUtilService, programmingExerciseCreationScheduleService, Duration.ofSeconds(10), Duration.ofMillis(5));

        ProgrammingExerciseTestCase buildGate = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.CompileSort").weight(1.0);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("sort-test.push_then_pop").weight(1.0);
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(buildGate, behaviour));
        AtomicInteger matchingResultPolls = new AtomicInteger();
        when(programmingSubmissionService.existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any()))
                .thenAnswer(invocation -> matchingResultPolls.incrementAndGet() >= 2);

        long startNanos = System.nanoTime();
        promptService.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), ""));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(buildGate.getWeight()).as("build gate zero-weighted even though the count never moved off the pre-build value").isEqualTo(0.0);
        assertThat(elapsed).as("did not spin the full sync timeout on a same-count re-sync").isLessThan(Duration.ofSeconds(2));
        verify(programmingSubmissionService, atMost(4)).existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any());
    }

    @Test
    void persist_buildResultNeverLands_failsAtTheDeadlineInsteadOfAcceptingPotentiallyWrongGrading() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        Duration timeout = Duration.ofMillis(500);
        GenerationPersistenceService boundedService = new GenerationPersistenceService("main", gitService, repositoryService, participationService,
                continuousIntegrationTriggerService, programmingSubmissionService, exerciseVersionService, testCaseRepository, resultRepository, programmingExerciseRepository,
                programmingExerciseTaskService, tempFileUtilService, programmingExerciseCreationScheduleService, timeout, Duration.ofMillis(5));

        AtomicInteger matchingResultPolls = new AtomicInteger();
        when(programmingSubmissionService.existsNewerSuccessfulTestResultForParticipationAndCommitHash(anyLong(), eq("hash-tests"), any())).thenAnswer(invocation -> {
            matchingResultPolls.incrementAndGet();
            return false;
        });
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of());

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> boundedService.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), "")))
                .isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("Timed out waiting for the tests-build result");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(elapsed).as("terminated at the bounded deadline, did not hang").isLessThan(Duration.ofSeconds(5));
        assertThat(matchingResultPolls.get()).as("kept polling for the matching TEST build result until the deadline").isGreaterThan(1);
    }

    @Test
    void persist_removesOrphanedCanonicalTestSources_soPersistedTreeMirrorsTheSandbox() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        Map<String, FileType> trackedTestFiles = Map.of("test/de/test/StringsTest.java", FileType.FILE, "test/de/test/SortingExampleBehaviorTest.java", FileType.FILE,
                "test/de/test/test.json", FileType.FILE, "test/de/test/OldClassTest.java", FileType.FILE, "pom.xml", FileType.FILE, "test", FileType.FOLDER);
        when(repositoryService.getFiles(any())).thenReturn(Map.of(), Map.of(), trackedTestFiles);

        Map<String, String> producedTests = Map.of("test/de/test/StringsTest.java", "...", "test/de/test/test.json", "[]", "pom.xml", "<project/>");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), producedTests, "");

        service.persist(exercise, user, outcome);

        verify(repositoryService).deleteFile(repository, "test/de/test/SortingExampleBehaviorTest.java");
        verify(repositoryService).deleteFile(repository, "test/de/test/OldClassTest.java");
        verify(repositoryService, never()).deleteFile(repository, "pom.xml");
        verify(repositoryService, never()).deleteFile(repository, "test/de/test/test.json");
    }

    @Test
    void persist_whenOrphanDeleteFails_abortsInsteadOfCommittingUnverifiedOverlay() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        Map<String, FileType> trackedTemplateFiles = Map.of("src/de/test/Generated.java", FileType.FILE, "src/de/test/Stale.java", FileType.FILE);
        when(repositoryService.getFiles(any())).thenReturn(trackedTemplateFiles);
        Mockito.doThrow(new java.io.IOException("locked")).when(repositoryService).deleteFile(repository, "src/de/test/Stale.java");

        GenerationOutcome outcome = outcomeWith(Map.of("src/de/test/Generated.java", "class Generated {}"), Map.of(), Map.of(), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("orphaned")
                .hasMessageContaining("INCOMPLETE");

        verify(repositoryService, never()).commitChanges(any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persist_preservesScaffoldedBinary_neverDeletedAsOrphanNorRewritten(@org.junit.jupiter.api.io.TempDir java.nio.file.Path workingTree) throws Exception {
        java.nio.file.Path wrapperDir = workingTree.resolve("gradle/wrapper");
        byte[] wrapperBytes = { 0x50, 0x4B, 0x03, 0x04, 0, 1, 2, (byte) 0xFF, (byte) 0x89 };
        org.apache.commons.io.FileUtils.writeByteArrayToFile(wrapperDir.resolve("gradle-wrapper.jar").toFile(), wrapperBytes);
        Path runScript = workingTree.resolve("run.sh");
        FileUtils.writeStringToFile(runScript.toFile(), "#!/bin/sh\necho old\n", StandardCharsets.UTF_8);
        assertThat(runScript.toFile().setExecutable(true, false)).isTrue();

        stubSuccessfulCheckoutAndCommits();
        when(repository.getLocalPath()).thenReturn(workingTree);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        Map<String, FileType> trackedTemplateFiles = Map.of("src/de/test/BankAccount.java", FileType.FILE, "gradle/wrapper/gradle-wrapper.jar", FileType.FILE, "run.sh",
                FileType.FILE);
        when(repositoryService.getFiles(any())).thenReturn(trackedTemplateFiles, Map.of(), Map.of());
        when(gitService.getFileByName(eq(repository), anyString()))
                .thenAnswer(invocation -> java.nio.file.Files.exists(workingTree.resolve(invocation.getArgument(1, String.class)))
                        ? Optional.of(mock(de.tum.cit.aet.artemis.programming.domain.File.class))
                        : Optional.empty());
        doAnswer(invocation -> {
            java.nio.file.Files.deleteIfExists(workingTree.resolve(invocation.getArgument(1, String.class)));
            return null;
        }).when(repositoryService).deleteFile(eq(repository), anyString());
        doAnswer(invocation -> {
            Path target = workingTree.resolve(invocation.getArgument(1, String.class));
            java.nio.file.Files.createDirectories(target.getParent());
            try (InputStream content = invocation.getArgument(2)) {
                FileUtils.copyInputStreamToFile(content, target.toFile());
            }
            return null;
        }).when(repositoryService).createFile(eq(repository), anyString(), any());

        Map<String, String> producedTemplate = Map.of("src/de/test/BankAccount.java", "class BankAccount {}", "run.sh", "#!/bin/sh\necho generated\n");
        GenerationOutcome outcome = outcomeWith(producedTemplate, Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        service.persist(exercise, user, outcome);

        verify(repositoryService, never()).deleteFile(repository, "gradle/wrapper/gradle-wrapper.jar");
        verify(repositoryService, never()).createFile(eq(repository), eq("gradle/wrapper/gradle-wrapper.jar"), any());
        assertThat(java.nio.file.Files.readAllBytes(wrapperDir.resolve("gradle-wrapper.jar"))).as("scaffolded wrapper jar is byte-identical").containsExactly(wrapperBytes);
        assertThat(java.nio.file.Files.isExecutable(runScript)).isTrue();
        verify(repositoryService).createFile(eq(repository), eq("src/de/test/BankAccount.java"), any());
    }

    @Test
    void generateDeletesStaleBinaryFromTheClearedSourceRoot(@org.junit.jupiter.api.io.TempDir Path workingTree) throws Exception {
        Path staleBinary = workingTree.resolve("src/main/resources/old-solution.bin");
        FileUtils.writeByteArrayToFile(staleBinary.toFile(), new byte[] { 0, 1, 2, 3 });
        stubSuccessfulCheckoutAndCommits();
        when(repository.getLocalPath()).thenReturn(workingTree);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        when(repositoryService.getFiles(any())).thenReturn(Map.of("src/Main.java", FileType.FILE, "src/main/resources/old-solution.bin", FileType.FILE), Map.of(), Map.of());

        service.persist(exercise, user, outcomeWith(Map.of("src/Main.java", "class Main {}"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), ""));

        verify(repositoryService).deleteFile(repository, "src/main/resources/old-solution.bin");
    }

    @Test
    void persist_deletesOrphanedHarnessFile_whenAbsentFromProducedFiles() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        Map<String, FileType> trackedTestFiles = Map.of("test/de/test/StringsTest.java", FileType.FILE, "pom.xml", FileType.FILE);
        when(repositoryService.getFiles(any())).thenReturn(Map.of(), Map.of(), trackedTestFiles);

        Map<String, String> producedTests = Map.of("test/de/test/StringsTest.java", "...");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), producedTests, "");

        service.persist(exercise, user, outcome);

        verify(repositoryService).deleteFile(repository, "pom.xml");
    }

    @Test
    void persist_deletesOrphanedHarnessNamedFilesOutsideTheTestsRepository() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        when(repositoryService.getFiles(any())).thenReturn(Map.of("pom.xml", FileType.FILE), Map.of("package.json", FileType.FILE), Map.of());

        service.persist(exercise, user,
                outcomeWith(Map.of("src/de/test/Template.java", "t"), Map.of("src/de/test/Solution.java", "s"), Map.of("test/de/test/SolutionTest.java", "x"), ""));

        verify(repositoryService).deleteFile(repository, "pom.xml");
        verify(repositoryService).deleteFile(repository, "package.json");
    }

    @Test
    void persist_keepsARepositoryFileNamedProblemStatementDistinctFromExerciseMetadata() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        when(repositoryService.getFiles(repository)).thenReturn(Map.of("problem-statement.md", FileType.FILE));
        when(gitService.getLocalHeadHash(repository)).thenReturn("pre-tests");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("hash-tests");
        exerciseProblemStatement.set("old statement");

        service.persist(exercise, user, outcomeWith(Map.of(), Map.of(), Map.of("problem-statement.md", "stale duplicate", "Test.java", "x"), "new statement"));

        verify(repositoryService).createFile(eq(repository), eq("problem-statement.md"), any());
        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "new statement", null, "old statement", null);
    }

    @Test
    void persist_problemStatementUnchanged_doesNotRewriteIt() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "same statement");
        exerciseProblemStatement.set("same statement");

        service.persist(exercise, user, outcome);

        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
    }

    @Test
    void resyncAfterRevert_refusesToRestoreMetadataWhenItChangedAfterTheRevertStarted() {
        ProgrammingExercise currentExercise = new ProgrammingExercise();
        currentExercise.setProblemStatement("manual edit");
        currentExercise.setTitle("Adapted Title");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentExercise));

        boolean result = service.resyncAfterRevert(exercise, user, null, "old statement", "Old Title", "adapted statement", "Adapted Title");

        assertThat(result).isFalse();
        verify(programmingExerciseRepository, never()).updateProblemStatementAndTitleIfUnchanged(anyLong(), any(), any(), any(), any());
        verify(programmingExerciseTaskService, never()).updateTasksFromProblemStatement(any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void resyncAfterRevert_usesExactCurrentMetadataForNormalizedSafeRestore() {
        ProgrammingExercise currentExercise = new ProgrammingExercise();
        currentExercise.setProblemStatement("adapted statement\r\n");
        currentExercise.setTitle("Adapted Title ");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentExercise));
        when(programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(1L, "old statement", "Old Title", "adapted statement\r\n", "Adapted Title")).thenReturn(1);

        boolean result = service.resyncAfterRevert(exercise, user, null, "old statement", "Old Title", "adapted statement\n", "Adapted Title");

        assertThat(result).isTrue();
        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "old statement", "Old Title", "adapted statement\r\n", "Adapted Title");
        verify(programmingExerciseTaskService).updateTasksFromProblemStatement(exercise);
        verify(exerciseVersionService).createExerciseVersionOrThrow(exercise, user);
    }

    @Test
    void resyncAfterRevert_recordsCallerCapturedBranchHeadsInTheVersion() {
        ProgrammingExercise currentExercise = new ProgrammingExercise();
        currentExercise.setProblemStatement("adapted statement");
        currentExercise.setTitle("Adapted Title");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentExercise));
        when(programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(1L, "old statement", "Old Title", "adapted statement", "Adapted Title")).thenReturn(1);
        Map<RepositoryType, String> revertedHeads = Map.of(RepositoryType.TEMPLATE, "release-template", RepositoryType.SOLUTION, "release-solution", RepositoryType.TESTS,
                "release-tests");

        boolean result = service.resyncAfterRevertWithSignal(exercise, user, null, "old statement", "Old Title", "adapted statement", "Adapted Title", revertedHeads);

        assertThat(result).isTrue();
        verify(exerciseVersionService).createExerciseVersionOrThrow(exercise, user, revertedHeads);
    }

    @Test
    void canRestoreProblemStatementAndTitle_refusesPersistedManualMetadataEdit() {
        ProgrammingExercise currentExercise = new ProgrammingExercise();
        currentExercise.setProblemStatement("manual edit");
        currentExercise.setTitle("Adapted Title");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentExercise));

        boolean result = service.canRestoreProblemStatementAndTitle(exercise, "old statement", "Old Title", "adapted statement", "Adapted Title");

        assertThat(result).isFalse();
    }

    @Test
    void canRestoreProblemStatementAndTitle_allowsOnlyNormalizedPersistedMetadataDifference() {
        ProgrammingExercise currentExercise = new ProgrammingExercise();
        currentExercise.setProblemStatement("adapted statement\r\n");
        currentExercise.setTitle("Adapted Title ");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentExercise));

        boolean result = service.canRestoreProblemStatementAndTitle(exercise, "old statement", "Old Title", "adapted statement\n", "Adapted Title");

        assertThat(result).isTrue();
    }

    @Test
    void canRestoreProblemStatementAndTitle_refusesMissingExerciseRow() {
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.empty());

        boolean result = service.canRestoreProblemStatementAndTitle(exercise, "old statement", "Old Title", "adapted statement", "Adapted Title");

        assertThat(result).isFalse();
    }

    @Test
    void canRestoreProblemStatementAndTitle_refusesMixedExpectedAndTargetMetadataState() {
        ProgrammingExercise currentExercise = new ProgrammingExercise();
        currentExercise.setProblemStatement("adapted statement");
        currentExercise.setTitle("Old Title");
        when(programmingExerciseRepository.findById(1L)).thenReturn(Optional.of(currentExercise));

        boolean result = service.canRestoreProblemStatementAndTitle(exercise, "old statement", "Old Title", "adapted statement", "Adapted Title");

        assertThat(result).isFalse();
    }

    @Test
    void persist_propagatesMidSequenceCommitFailure_andDoesNotCreateVersion() throws Exception {
        Repository repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq("main"), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("template-pre", "solution-pre", "solution-post");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("template-post", "solution-post");
        Mockito.doNothing().doThrow(new org.eclipse.jgit.api.errors.NoHeadException("boom")).when(gitService).pushCommitWithLease(any(), anyString(), anyString(), anyString());
        when(gitService.getLastCommitHash(solutionUri, "main")).thenReturn("solution-pre");

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("Failed to commit");
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
        verify(gitService).resetToOriginHead(repository);
    }

    @Test
    void persist_revertsCommitWhenPushSucceededButCommitCallReportedFailure() throws Exception {
        Repository repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq("main"), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("template-before", "template-after");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("template-after");
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("template-after");
        Mockito.doThrow(new org.eclipse.jgit.api.errors.NoHeadException("push result was lost")).when(gitService).pushCommitWithLease(any(), anyString(), anyString(), anyString());

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class);
        verify(gitService).resetToCommitAndForcePush(repository, "template-before", "template-after", "main");
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persist_surfacesManualReviewWhenRemoteAdvancedAfterAmbiguousCommitFailure() throws Exception {
        Repository repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq("main"), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("template-before", "template-after");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("template-after");
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("manual-after");
        Mockito.doThrow(new org.eclipse.jgit.api.errors.NoHeadException("push result was lost")).when(gitService).pushCommitWithLease(any(), anyString(), anyString(), anyString());

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOfSatisfying(GenerationIncompleteException.class, thrown -> {
            assertThat(thrown).hasMessageContaining("requires manual review");
            // The push outcome is genuinely unknown (the local commit landed, but the remote branch could not be confirmed to still be at its pre-persist state): the live
            // exercise must be conservatively reported as changed, and the best-known (unconfirmed) commit hash surfaced instead of an empty, falsely-reassuring commit map.
            assertThat(thrown.liveExerciseChanged()).isTrue();
            assertThat(thrown.savedRepositoryCommits()).containsExactly(Map.entry(RepositoryType.TEMPLATE, "template-after"));
        });
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
    }

    @Test
    void persist_compensatesAlreadyCommittedReposInReverseOrder_whenLaterRepositoryFails() throws Exception {
        Repository repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq("main"), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("tmpl-pre", "sol-pre", "tests-pre", "tests-post");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("tmpl-post", "sol-post", "tests-post");
        when(gitService.getLastCommitHash(templateUri, "main")).thenReturn("tmpl-post");
        when(gitService.getLastCommitHash(solutionUri, "main")).thenReturn("sol-post");
        when(gitService.getLastCommitHash(testsUri, "main")).thenReturn("tests-pre");
        Mockito.doNothing().doNothing().doThrow(new org.eclipse.jgit.api.errors.NoHeadException("tests boom")).when(gitService).pushCommitWithLease(any(), anyString(), anyString(),
                anyString());

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class).hasMessageContaining("INCOMPLETE");

        InOrder order = Mockito.inOrder(gitService);
        order.verify(gitService).resetToCommitAndForcePush(repository, "sol-pre", "sol-post", "main");
        order.verify(gitService).resetToCommitAndForcePush(repository, "tmpl-pre", "tmpl-post", "main");
        verify(gitService, never()).resetToCommitAndForcePush(any(), eq("tests-pre"), any(), any());
        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persist_compensatesOnExerciseRepositoryBranch() throws Exception {
        String exerciseBranch = "release";
        ProgrammingExerciseBuildConfig buildConfig = mock(ProgrammingExerciseBuildConfig.class);
        when(buildConfig.getBranch()).thenReturn(exerciseBranch);
        when(exercise.getBuildConfig()).thenReturn(buildConfig);
        Repository repository = mock(Repository.class);
        stubRepositoryWorkingTree(repository);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), any(LocalVCRepositoryUri.class), any(Path.class), eq(true), eq(exerciseBranch), eq(false)))
                .thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLocalHeadHash(repository)).thenReturn("template-pre", "solution-pre", "solution-post");
        when(gitService.commitStagedChanges(any(), anyString(), any())).thenReturn("template-post", "solution-post");
        when(gitService.getLastCommitHash(solutionUri, exerciseBranch)).thenReturn("solution-pre");
        when(gitService.getLastCommitHash(templateUri, exerciseBranch)).thenReturn("template-post");
        Mockito.doNothing().doThrow(new org.eclipse.jgit.api.errors.NoHeadException("boom")).when(gitService).pushCommitWithLease(any(), anyString(), anyString(), anyString());

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of(), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class);
        verify(gitService).resetToCommitAndForcePush(repository, "template-pre", "template-post", exerciseBranch);
    }

    private final String jobId = "job-42";

    private void stubAdaptTarget() {
        when(repositoryService.getFiles(any())).thenReturn(Map.of("src/de/test/BankAccount.java", FileType.FILE));
    }

    @Test
    void persist_whenProducedFilesHaveNoRepository_failsInsteadOfSilentlyDroppingThem() {
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(null);
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of(), Map.of(), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(GenerationIncompleteException.class)
                .hasMessageContaining("exercise repository is unavailable");

        verify(exerciseVersionService, never()).createExerciseVersionOrThrow(any(), any());
    }

    @Test
    void persist_writesVerifiedSourceBytesWithoutPostVerificationNormalization() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        String dirty = "public class X {\n    // verified bytes are committed unchanged\n    String message() { return \"size must be non‑negative\"; }\n}\n";

        service.persist(exercise, user, outcomeWith(Map.of("Template.java", "t"), Map.of("X.java", dirty), Map.of("Test.java", "x"), ""));

        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(repositoryService, atLeastOnce()).createFile(any(), eq("X.java"), captor.capture());
        String written = new String(captor.getValue().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(written).isEqualTo(dirty);
    }

    @Test
    void extractTitleFromH1_returnsFirstLevelOneHeading_ignoringDeeperHeadingsAndLeadingBlankLines() {
        assertThat(GenerationPersistenceService.extractTitleFromH1("# Roman Numerals\n\nConvert integers 1..3999.")).isEqualTo("Roman Numerals");
        assertThat(GenerationPersistenceService.extractTitleFromH1("\n\n#   Balanced BST   \n## Tasks")).isEqualTo("Balanced BST");
        assertThat(GenerationPersistenceService.extractTitleFromH1("## Subsection only\nbody")).isNull();
        assertThat(GenerationPersistenceService.extractTitleFromH1("No heading at all")).isNull();
        assertThat(GenerationPersistenceService.extractTitleFromH1("#NotAHeading")).isNull();
        String longHeading = "# " + "x".repeat(400);
        assertThat(GenerationPersistenceService.extractTitleFromH1(longHeading)).hasSize(255);
    }

    @Test
    void persist_fromScratchGeneration_reconcilesTitleFromGeneratedH1() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        exerciseProblemStatement.set("");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "# Roman Numerals\n\nConvert 1..3999.");

        service.persist(exercise, user, outcome);

        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "# Roman Numerals\n\nConvert 1..3999.", "Roman Numerals", "", null);
        verify(exercise).setTitle("Roman Numerals");
    }

    @Test
    void persist_adaptGeneration_keepsTheInstructorTitle_neverReconcilesFromH1() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        exerciseProblemStatement.set("# Old Title\n\nold body");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "# Brand New Heading\n\nnew body");

        service.persist(exercise, user, outcome);

        verify(exercise, never()).setTitle(any());
        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "# Brand New Heading\n\nnew body", null, "# Old Title\n\nold body", null);
    }

    @Test
    void persist_adaptWithBlankProblemStatementStillKeepsTheInstructorTitle() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        exerciseProblemStatement.set("");
        exerciseTitle.set("Instructor title");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "# Generated heading\n\nnew body");

        service.persist(exercise, user, outcome, "", "Instructor title", "job-1", GenerationMode.ADAPT, () -> true);

        verify(programmingExerciseRepository).updateProblemStatementAndTitleIfUnchanged(1L, "# Generated heading\n\nnew body", "Instructor title", "", "Instructor title");
        verify(exercise, never()).setTitle("Generated heading");
    }
}
