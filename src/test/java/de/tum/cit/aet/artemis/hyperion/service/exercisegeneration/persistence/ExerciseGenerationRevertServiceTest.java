package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Tests repository reset and retry behavior against canonical recovery pairs, with Git and persistence mocked.
 */
class ExerciseGenerationRevertServiceTest {

    private static final String DEFAULT_BRANCH = "main";

    private GenerationVersionRecoveryService recovery;

    private GenerationRestoreMetadataService metadata;

    private GenerationRestoreTestCasesService testCases;

    private GitService gitService;

    private GenerationPersistenceService persistenceService;

    private TempFileUtilService tempFileUtilService;

    private ExerciseGenerationRevertService revertService;

    private ProgrammingExercise exercise;

    private User user;

    private LocalVCRepositoryUri templateUri;

    private LocalVCRepositoryUri solutionUri;

    private LocalVCRepositoryUri testsUri;

    private Repository templateRepo;

    private Repository solutionRepo;

    private Repository testsRepo;

    @BeforeEach
    void setUp() throws Exception {
        recovery = mock(GenerationVersionRecoveryService.class);
        metadata = mock(GenerationRestoreMetadataService.class);
        testCases = mock(GenerationRestoreTestCasesService.class);
        when(testCases.canRestore(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(true);
        when(metadata.canRestore(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(true);
        gitService = mock(GitService.class);
        persistenceService = mock(GenerationPersistenceService.class);
        tempFileUtilService = new TempFileUtilService(Path.of("build/tmp/hyperion-adaptation-revert-test"));
        when(persistenceService.canRestoreGrading(any(Long.class), any(), any())).thenReturn(true);
        when(persistenceService.canRestoreProblemStatementAndTitle(any(), any(), any(), any(), any())).thenReturn(true);
        when(persistenceService.resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any())).thenReturn(true);
        revertService = new ExerciseGenerationRevertService(recovery, metadata, testCases, gitService, persistenceService, tempFileUtilService, DEFAULT_BRANCH);

        templateUri = mock(LocalVCRepositoryUri.class);
        solutionUri = mock(LocalVCRepositoryUri.class);
        testsUri = mock(LocalVCRepositoryUri.class);
        templateRepo = mock(Repository.class);
        solutionRepo = mock(Repository.class);
        testsRepo = mock(Repository.class);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(77L);
        when(exercise.getProblemStatement()).thenReturn("adapted statement");
        when(exercise.getTitle()).thenReturn("Adapted Title");
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(templateUri);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);
        when(exercise.getRepositoryURI(RepositoryType.TESTS)).thenReturn(testsUri);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("sha-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("sha-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("sha-tests");

        when(gitService.getOrCheckoutRepositoryOnBranch(eq(templateUri), any(Path.class), eq(DEFAULT_BRANCH))).thenReturn(templateRepo);
        when(gitService.getOrCheckoutRepositoryOnBranch(eq(solutionUri), any(Path.class), eq(DEFAULT_BRANCH))).thenReturn(solutionRepo);
        when(gitService.getOrCheckoutRepositoryOnBranch(eq(testsUri), any(Path.class), eq(DEFAULT_BRANCH))).thenReturn(testsRepo);

        user = new User();
        user.setLogin("instructor");
    }

    @Test
    void restoresCanonicalMetadataWithoutRequiringThePreviousDraftToBuild() {
        recordBaseline("job-empty-draft", GenerationMode.GENERATE, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        var result = revertService.revert(exercise, user, () -> true).orElseThrow();
        assertThat(result.fullyReverted()).isTrue();
        verify(persistenceService, never()).prepareTestsBuildSignal(any(), any());
        verify(persistenceService, never()).triggerTestsBuild(any(), any());
        verify(testCases).restore(eq(77L), any(), any());
        verify(recovery).consumed(any());
    }

    @Test
    void recordBaseline_omitsRepositoriesWithoutACapturedHead() throws Exception {
        Map<RepositoryType, String> onlySolutionAndTests = new EnumMap<>(RepositoryType.class);
        onlySolutionAndTests.put(RepositoryType.SOLUTION, "sha-solution");
        onlySolutionAndTests.put(RepositoryType.TESTS, "sha-tests");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests");

        recordBaseline("job-1", GenerationMode.ADAPT, onlySolutionAndTests, postRunHeads(RepositoryType.SOLUTION, "adapted-solution", RepositoryType.TESTS, "adapted-tests"),
                "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).isPresent();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.SOLUTION, RepositoryType.TESTS);
        verify(gitService).resetToCommitAndForcePush(solutionRepo, "sha-solution", "adapted-solution", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(testsRepo, "sha-tests", "adapted-tests", DEFAULT_BRANCH);
        verify(gitService, never()).resetToCommitAndForcePush(eq(templateRepo), any(), any(), any());
    }

    @Test
    void revertDoesNotResetRepositoriesWhenGradingWasEditedAfterGeneration() throws Exception {
        recordBaseline("job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        when(persistenceService.canRestoreGrading(any(Long.class), any(), any())).thenReturn(false);

        assertThat(revertService.revert(exercise, user, () -> true)).hasValueSatisfying(result -> assertThat(result.fullyReverted()).isFalse());

        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");
    }

    @Test
    void noRepositoryCanChangeWithoutADurableRestorationObligation() throws Exception {
        recordBaseline("job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        org.mockito.Mockito.doThrow(new IllegalStateException("journal unavailable")).when(recovery).started(any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> revertService.revert(exercise, user, () -> true)).isInstanceOf(IllegalStateException.class);
        verify(gitService, never()).getOrCheckoutRepositoryOnBranch(any(), any(Path.class), any());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
    }

    @Test
    void canonicalTestMetadataConflictIsACleanRefusalBeforeAnyRepositoryWrite() throws Exception {
        recordBaseline("job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        when(testCases.canRestore(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(false);

        assertThat(revertService.revert(exercise, user, () -> true)).hasValueSatisfying(result -> {
            assertThat(result.fullyReverted()).isFalse();
            assertThat(result.mutationAttempted()).isFalse();
        });

        verify(gitService, never()).getOrCheckoutRepositoryOnBranch(any(), any(Path.class), any());
        verify(testCases, never()).restore(org.mockito.ArgumentMatchers.anyLong(), any(), any());
        verify(recovery, never()).consumed(any());
    }

    @Test
    void recordBaseline_retainsTheRunModeForStatusRecovery() {
        boolean recorded = recordBaseline("job-generate", GenerationMode.GENERATE, preRunHeads(), postRunHeads(), "old statement", "Old Title");

        assertThat(recorded).isTrue();
        assertThat(revertService.findRevertibleRun(77L)).contains(new ExerciseGenerationRevertService.RevertibleRun("job-generate", GenerationMode.GENERATE));
    }

    @Test
    void revert_resetsEveryRepositoryToItsCapturedSha_andResyncsAndConsumesTheBaseline() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests");
        recordBaseline("job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isTrue();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
        verify(gitService).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(solutionRepo, "sha-solution", "adapted-solution", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(testsRepo, "sha-tests", "adapted-tests", DEFAULT_BRANCH);
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement"), eq("Adapted Title"),
                eq(preRunHeads()), any(), any(), any());
        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();
        assertThat(revertService.revert(exercise, user, () -> true)).isEmpty();
    }

    @Test
    void revert_usesBranchCapturedWithBaseline() throws Exception {
        Repository releaseTemplateRepo = mock(Repository.class);
        when(gitService.getOrCheckoutRepositoryOnBranch(eq(templateUri), any(Path.class), eq("release"))).thenReturn(releaseTemplateRepo);
        when(gitService.getLastCommitHash(templateUri, "release")).thenReturn("adapted-template");
        when(gitService.getLastCommitHash(solutionUri, "release")).thenReturn("release-solution");
        when(gitService.getLastCommitHash(testsUri, "release")).thenReturn("release-tests");
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title", "release");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).hasValueSatisfying(value -> assertThat(value.fullyReverted()).isTrue());
        verify(gitService).resetToCommitAndForcePush(releaseTemplateRepo, "sha-template", "adapted-template", "release");
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement"), eq("Adapted Title"),
                eq(Map.of(RepositoryType.TEMPLATE, "sha-template", RepositoryType.SOLUTION, "release-solution", RepositoryType.TESTS, "release-tests")), any(), any(), any());
    }

    @Test
    void revert_checksOutRepositoryIntoAnIsolatedTemporaryPath() throws Exception {
        Repository cachedTemplateRepo = mock(Repository.class);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        when(gitService.getOrCheckoutRepository(templateUri, false, DEFAULT_BRANCH, false)).thenReturn(cachedTemplateRepo);
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title");

        revertService.revert(exercise, user, () -> true);

        ArgumentCaptor<Path> checkoutPath = ArgumentCaptor.forClass(Path.class);
        verify(gitService).getOrCheckoutRepositoryOnBranch(eq(templateUri), checkoutPath.capture(), eq(DEFAULT_BRANCH));
        assertThat(checkoutPath.getValue().getFileName()).hasToString("repository");
        assertThat(checkoutPath.getValue().getParent()).doesNotExist();
        verify(templateRepo).closeBeforeDelete();
        verify(gitService).fetchAll(cachedTemplateRepo);
        verify(gitService).reset(cachedTemplateRepo, "origin/" + DEFAULT_BRANCH);
    }

    @Test
    void revert_deletesCachedCheckoutWhenRefreshFails() throws Exception {
        Repository cachedTemplateRepo = mock(Repository.class);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        when(gitService.getOrCheckoutRepository(templateUri, false, DEFAULT_BRANCH, false)).thenReturn(cachedTemplateRepo);
        org.mockito.Mockito.doThrow(new org.eclipse.jgit.api.errors.GitAPIException("fetch failed") {
        }).when(gitService).fetchAll(cachedTemplateRepo);
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).hasValueSatisfying(revertResult -> assertThat(revertResult.fullyReverted()).isTrue());
        verify(gitService).deleteLocalRepository(templateUri);
    }

    @Test
    void revert_whenResyncFailsAfterRepositoryReset_reportsPartialAndKeepsBaselineForRetry() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template", "sha-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution", "sha-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests", "sha-tests");
        recordBaseline("job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        when(persistenceService.resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any())).thenReturn(false, true);

        Optional<ExerciseGenerationRevertService.RevertResult> partial = revertService.revert(exercise, user, () -> true);
        Optional<ExerciseGenerationRevertService.RevertResult> retry = revertService.revert(exercise, user, () -> true);

        assertThat(partial).isPresent();
        assertThat(partial.get().fullyReverted()).isFalse();
        assertThat(partial.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
        assertThat(retry).isPresent();
        assertThat(retry.get().fullyReverted()).isTrue();
    }

    @Test
    void revert_whenARepositoryFails_keepsBaselineForRetry() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template", "sha-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests", "sha-tests");
        recordBaseline("job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        when(gitService.getOrCheckoutRepositoryOnBranch(eq(solutionUri), any(Path.class), eq(DEFAULT_BRANCH))).thenThrow(new IllegalStateException("checkout failed"))
                .thenReturn(solutionRepo);

        Optional<ExerciseGenerationRevertService.RevertResult> partial = revertService.revert(exercise, user, () -> true);
        Optional<ExerciseGenerationRevertService.RevertResult> retry = revertService.revert(exercise, user, () -> true);

        assertThat(partial).isPresent();
        assertThat(partial.get().fullyReverted()).isFalse();
        assertThat(partial.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE);
        assertThat(retry).isPresent();
        verify(gitService, times(1)).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);
        verify(gitService, times(1)).resetToCommitAndForcePush(testsRepo, "sha-tests", "adapted-tests", DEFAULT_BRANCH);
        verify(persistenceService, never()).triggerTestsBuild(eq(exercise), any());
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement"), eq("Adapted Title"),
                anyMap(), any(), any(), any());
    }

    @Test
    void revert_allowsOnlyLineEndingAndOuterWhitespaceMetadataDifferences() throws Exception {
        when(exercise.getProblemStatement()).thenReturn("adapted statement\r\n");
        when(exercise.getTitle()).thenReturn(" Adapted Title ");
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isTrue();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE);
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement\r\n"),
                eq(" Adapted Title "), anyMap(), any(), any(), any());
    }

    @Test
    void revert_refusesToClobberManualCommitsAfterTheAdaptation() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("manual-template");
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isFalse();
        assertThat(result.get().revertedRepositories()).isEmpty();
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any());
        ArgumentCaptor<Path> checkoutPath = ArgumentCaptor.forClass(Path.class);
        verify(gitService).getOrCheckoutRepositoryOnBranch(eq(templateUri), checkoutPath.capture(), eq(DEFAULT_BRANCH));
        assertThat(checkoutPath.getValue().getParent()).doesNotExist();
        verify(templateRepo).closeBeforeDelete();
    }

    @Test
    void revert_refusesToClobberManualProblemStatementEditsAfterTheAdaptation() throws Exception {
        when(exercise.getProblemStatement()).thenReturn("adapted statement", "manual statement");
        when(exercise.getTitle()).thenReturn("Adapted Title", "Adapted Title");
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isFalse();
        assertThat(result.get().revertedRepositories()).isEmpty();
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any());
    }

    @Test
    void revert_refusesBeforeRepositoryResetWhenPersistedMetadataChanged() throws Exception {
        when(persistenceService.canRestoreGrading(any(Long.class), any(), any())).thenReturn(true);
        when(persistenceService.canRestoreProblemStatementAndTitle(any(), any(), any(), any(), any())).thenReturn(false);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        recordBaseline("job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"), "old statement",
                "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> true);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isFalse();
        assertThat(result.get().revertedRepositories()).isEmpty();
        verify(gitService, never()).getOrCheckoutRepositoryOnBranch(any(), any(Path.class), any());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any());
    }

    @Test
    void revert_whenOwnershipIsLostBeforeFirstReset_doesNotMutateAndKeepsBaseline() throws Exception {
        recordBaseline("job-1", GenerationMode.GENERATE, preRunHeads(), postRunHeads(), "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> false);

        assertThat(result).hasValueSatisfying(value -> {
            assertThat(value.fullyReverted()).isFalse();
            assertThat(value.revertedRepositories()).isEmpty();
        });
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");
        verify(gitService, never()).getOrCheckoutRepositoryOnBranch(any(), any(Path.class), any());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any());
    }

    @Test
    void revert_whenOwnershipIsLostAfterOneReset_returnsPartialAndKeepsBaseline() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        recordBaseline("job-1", GenerationMode.GENERATE, Map.of(RepositoryType.TEMPLATE, "sha-template", RepositoryType.SOLUTION, "sha-solution"),
                Map.of(RepositoryType.TEMPLATE, "adapted-template", RepositoryType.SOLUTION, "adapted-solution"), "old statement", "Old Title");
        AtomicBoolean owned = new AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            owned.set(false);
            return null;
        }).when(gitService).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, owned::get);

        assertThat(result).hasValueSatisfying(value -> {
            assertThat(value.fullyReverted()).isFalse();
            assertThat(value.revertedRepositories()).containsExactly(RepositoryType.TEMPLATE);
        });
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");
        verify(gitService).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);
        verify(gitService, never()).resetToCommitAndForcePush(eq(solutionRepo), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any());
    }

    @Test
    void revert_whenNoBaselineRetained_returnsEmpty() {
        assertThat(revertService.revert(exercise, user, () -> true)).isEmpty();
    }

    /** Records a baseline the way a completed run does: what the exercise carries right now is what that run wrote, on the default branch. */
    private boolean recordBaseline(String jobId, GenerationMode mode, Map<RepositoryType, String> preRunHeads, Map<RepositoryType, String> postRunHeads,
            String problemStatementBeforeRun, String titleBeforeRun) {
        return recordBaseline(jobId, mode, preRunHeads, postRunHeads, problemStatementBeforeRun, titleBeforeRun, DEFAULT_BRANCH);
    }

    private boolean recordBaseline(String jobId, GenerationMode mode, Map<RepositoryType, String> preRunHeads, Map<RepositoryType, String> postRunHeads,
            String problemStatementBeforeRun, String titleBeforeRun, String repositoryBranch) {
        var baseline = new ExerciseGenerationBaseline(jobId, mode, preRunHeads, postRunHeads, problemStatementBeforeRun, titleBeforeRun, exercise.getProblemStatement(),
                exercise.getTitle(), repositoryBranch, GenerationGrading.Snapshot.EMPTY, GenerationGrading.Snapshot.EMPTY);
        var pair = new GenerationVersionRecoveryService.Recovery(jobId, 82L, null, null, baseline);
        when(recovery.find(exercise.getId())).thenReturn(Optional.of(pair));
        org.mockito.Mockito.doAnswer(invocation -> {
            when(recovery.find(exercise.getId())).thenReturn(Optional.empty());
            return null;
        }).when(recovery).consumed(pair);
        return true;
    }

    private static Map<RepositoryType, String> postRunHeads() {
        return postRunHeads(RepositoryType.TEMPLATE, "adapted-template", RepositoryType.SOLUTION, "adapted-solution", RepositoryType.TESTS, "adapted-tests");
    }

    private static Map<RepositoryType, String> postRunHeads(RepositoryType firstType, String firstHead, RepositoryType secondType, String secondHead) {
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        heads.put(firstType, firstHead);
        heads.put(secondType, secondHead);
        return heads;
    }

    private static Map<RepositoryType, String> postRunHeads(RepositoryType firstType, String firstHead, RepositoryType secondType, String secondHead, RepositoryType thirdType,
            String thirdHead) {
        Map<RepositoryType, String> heads = postRunHeads(firstType, firstHead, secondType, secondHead);
        heads.put(thirdType, thirdHead);
        return heads;
    }

    /** The pre-persist commit heads an accepted adaptation of all three repositories hands back to {@code recordBaseline}. */
    private static Map<RepositoryType, String> preRunHeads() {
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        heads.put(RepositoryType.TEMPLATE, "sha-template");
        heads.put(RepositoryType.SOLUTION, "sha-solution");
        heads.put(RepositoryType.TESTS, "sha-tests");
        return heads;
    }
}
