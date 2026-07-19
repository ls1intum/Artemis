package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Unit test for {@link ExerciseGenerationRevertService}'s capture-and-revert invariants against a real isolated embedded Hazelcast instance, with the git and persistence
 * collaborators mocked so the reset-to-captured-SHA behaviour is exercised deterministically without a real repository.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExerciseGenerationRevertServiceTest {

    private static final String DEFAULT_BRANCH = "main";

    private HazelcastInstance hazelcastInstance;

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

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("hyperion-revert-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void setUp() throws Exception {
        hazelcastInstance.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        gitService = mock(GitService.class);
        persistenceService = mock(GenerationPersistenceService.class);
        tempFileUtilService = new TempFileUtilService(Path.of("build/tmp/hyperion-adaptation-revert-test"));
        when(persistenceService.canRestoreProblemStatementAndTitle(any(), any(), any(), any(), any())).thenReturn(true);
        when(persistenceService.resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap())).thenReturn(true);
        revertService = new ExerciseGenerationRevertService(hazelcastInstance, gitService, persistenceService, tempFileUtilService, DEFAULT_BRANCH);
        revertService.init();

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

        when(gitService.getOrCheckoutRepository(eq(templateUri), eq(templateUri), any(Path.class), eq(true), eq(DEFAULT_BRANCH), eq(false))).thenReturn(templateRepo);
        when(gitService.getOrCheckoutRepository(eq(solutionUri), eq(solutionUri), any(Path.class), eq(true), eq(DEFAULT_BRANCH), eq(false))).thenReturn(solutionRepo);
        when(gitService.getOrCheckoutRepository(eq(testsUri), eq(testsUri), any(Path.class), eq(true), eq(DEFAULT_BRANCH), eq(false))).thenReturn(testsRepo);

        user = new User();
        user.setLogin("instructor");
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @Test
    void recordBaseline_omitsRepositoriesWithoutACapturedHead() throws Exception {
        Map<RepositoryType, String> onlySolutionAndTests = new EnumMap<>(RepositoryType.class);
        onlySolutionAndTests.put(RepositoryType.SOLUTION, "sha-solution");
        onlySolutionAndTests.put(RepositoryType.TESTS, "sha-tests");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests");

        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, onlySolutionAndTests,
                postRunHeads(RepositoryType.SOLUTION, "adapted-solution", RepositoryType.TESTS, "adapted-tests"), "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.SOLUTION, RepositoryType.TESTS);
        verify(gitService).resetToCommitAndForcePush(solutionRepo, "sha-solution", "adapted-solution", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(testsRepo, "sha-tests", "adapted-tests", DEFAULT_BRANCH);
        verify(gitService, never()).resetToCommitAndForcePush(eq(templateRepo), any(), any(), any());
    }

    @Test
    void recordBaseline_retainsTheRunModeForStatusRecovery() {
        boolean recorded = revertService.recordBaseline(exercise, "job-generate", GenerationMode.GENERATE, preRunHeads(), postRunHeads(), "old statement", "Old Title");

        assertThat(recorded).isTrue();
        assertThat(revertService.findRevertibleRun(77L)).contains(new ExerciseGenerationRevertService.RevertibleRun("job-generate", GenerationMode.GENERATE));
    }

    @Test
    void recordBaseline_whenPostRunHeadCaptureFails_recordsNoPartialBaseline() throws Exception {
        Map<RepositoryType, String> templateAndSolution = new EnumMap<>(RepositoryType.class);
        templateAndSolution.put(RepositoryType.TEMPLATE, "sha-template");
        templateAndSolution.put(RepositoryType.SOLUTION, "sha-solution");
        boolean recorded = revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, templateAndSolution, Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        assertThat(recorded).isFalse();
        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();
        assertThat(revertService.revert(exercise, user)).isEmpty();
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void recordBaseline_whenNewBaselineCannotBeRecorded_removesOlderBaseline() {
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");

        revertService.recordBaseline(exercise, "job-2", GenerationMode.ADAPT, preRunHeads(), Map.of(), "adapted statement", "Adapted Title");

        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();
    }

    @Test
    void invalidateBaseline_removesAPreviouslyRecordedBaseline_soAnOlderRunCanNoLongerBeReverted() throws Exception {
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");

        revertService.invalidateBaseline(77L);

        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();
        assertThat(revertService.revert(exercise, user)).isEmpty();
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
    }

    @Test
    void invalidateBaseline_isIdempotentWhenNoBaselineIsRecorded() {
        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();

        revertService.invalidateBaseline(77L);

        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();
    }

    @Test
    void revert_resetsEveryRepositoryToItsCapturedSha_andResyncsAndConsumesTheBaseline() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isTrue();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
        verify(gitService).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(solutionRepo, "sha-solution", "adapted-solution", DEFAULT_BRANCH);
        verify(gitService).resetToCommitAndForcePush(testsRepo, "sha-tests", "adapted-tests", DEFAULT_BRANCH);
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement"), eq("Adapted Title"),
                eq(preRunHeads()));
        assertThat(revertService.findRevertibleJobId(77L)).isEmpty();
        assertThat(revertService.revert(exercise, user)).isEmpty();
    }

    @Test
    void revert_usesBranchCapturedWithBaseline() throws Exception {
        AtomicReference<String> exerciseBranch = new AtomicReference<>("release");
        ProgrammingExerciseBuildConfig buildConfig = mock(ProgrammingExerciseBuildConfig.class);
        when(buildConfig.getBranch()).thenAnswer(invocation -> exerciseBranch.get());
        when(exercise.getBuildConfig()).thenReturn(buildConfig);
        Repository releaseTemplateRepo = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(eq(templateUri), eq(templateUri), any(Path.class), eq(true), eq("release"), eq(false))).thenReturn(releaseTemplateRepo);
        when(gitService.getLastCommitHash(templateUri, "release")).thenReturn("adapted-template");
        when(gitService.getLastCommitHash(solutionUri, "release")).thenReturn("release-solution");
        when(gitService.getLastCommitHash(testsUri, "release")).thenReturn("release-tests");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");
        exerciseBranch.set("other");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).hasValueSatisfying(value -> assertThat(value.fullyReverted()).isTrue());
        verify(gitService).resetToCommitAndForcePush(releaseTemplateRepo, "sha-template", "adapted-template", "release");
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement"), eq("Adapted Title"),
                eq(Map.of(RepositoryType.TEMPLATE, "sha-template", RepositoryType.SOLUTION, "release-solution", RepositoryType.TESTS, "release-tests")));
    }

    @Test
    void revert_checksOutRepositoryIntoAnIsolatedTemporaryPath() throws Exception {
        Repository cachedTemplateRepo = mock(Repository.class);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        when(gitService.getOrCheckoutRepository(templateUri, false, DEFAULT_BRANCH, false)).thenReturn(cachedTemplateRepo);
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        revertService.revert(exercise, user);

        ArgumentCaptor<Path> checkoutPath = ArgumentCaptor.forClass(Path.class);
        verify(gitService).getOrCheckoutRepository(eq(templateUri), eq(templateUri), checkoutPath.capture(), eq(true), eq(DEFAULT_BRANCH), eq(false));
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
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).hasValueSatisfying(revertResult -> assertThat(revertResult.fullyReverted()).isTrue());
        verify(gitService).deleteLocalRepository(templateUri);
    }

    @Test
    void revert_whenResyncFailsAfterRepositoryReset_reportsPartialAndKeepsBaselineForRetry() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template", "sha-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution", "sha-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests", "sha-tests");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        when(persistenceService.resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap())).thenReturn(false, true);

        Optional<ExerciseGenerationRevertService.RevertResult> partial = revertService.revert(exercise, user);
        Optional<ExerciseGenerationRevertService.RevertResult> retry = revertService.revert(exercise, user);

        assertThat(partial).isPresent();
        assertThat(partial.get().fullyReverted()).isFalse();
        assertThat(partial.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
        assertThat(retry).isPresent();
        assertThat(retry.get().fullyReverted()).isTrue();
    }

    @Test
    void revert_whenARepositoryFails_keepsBaselineForRetry() throws Exception {
        GenerationPersistenceService.TestsBuildSignal signal = new GenerationPersistenceService.TestsBuildSignal(11L, "sha-tests", 17L);
        when(persistenceService.prepareTestsBuildSignal(exercise, "sha-tests")).thenReturn(signal);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template", "sha-template");
        when(gitService.getLastCommitHash(solutionUri, DEFAULT_BRANCH)).thenReturn("adapted-solution");
        when(gitService.getLastCommitHash(testsUri, DEFAULT_BRANCH)).thenReturn("adapted-tests", "sha-tests");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, preRunHeads(), postRunHeads(), "old statement", "Old Title");
        when(gitService.getOrCheckoutRepository(eq(solutionUri), eq(solutionUri), any(Path.class), eq(true), eq(DEFAULT_BRANCH), eq(false)))
                .thenThrow(new IllegalStateException("checkout failed")).thenReturn(solutionRepo);

        Optional<ExerciseGenerationRevertService.RevertResult> partial = revertService.revert(exercise, user);
        Optional<ExerciseGenerationRevertService.RevertResult> retry = revertService.revert(exercise, user);

        assertThat(partial).isPresent();
        assertThat(partial.get().fullyReverted()).isFalse();
        assertThat(partial.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE);
        assertThat(retry).isPresent();
        verify(gitService, times(1)).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);
        verify(gitService, times(1)).resetToCommitAndForcePush(testsRepo, "sha-tests", "adapted-tests", DEFAULT_BRANCH);
        verify(persistenceService, never()).triggerTestsBuild(exercise, signal);
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(signal), eq("old statement"), eq("Old Title"), eq("adapted statement"),
                eq("Adapted Title"), anyMap());
    }

    @Test
    void revert_allowsOnlyLineEndingAndOuterWhitespaceMetadataDifferences() throws Exception {
        when(exercise.getProblemStatement()).thenReturn("adapted statement\r\n");
        when(exercise.getTitle()).thenReturn(" Adapted Title ");
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isTrue();
        assertThat(result.get().revertedRepositories()).containsExactly(RepositoryType.TEMPLATE);
        verify(persistenceService).resyncAfterRevertWithSignal(eq(exercise), eq(user), eq(null), eq("old statement"), eq("Old Title"), eq("adapted statement\r\n"),
                eq(" Adapted Title "), anyMap());
    }

    @Test
    void revert_refusesToClobberManualCommitsAfterTheAdaptation() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("manual-template");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isFalse();
        assertThat(result.get().revertedRepositories()).isEmpty();
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap());
        ArgumentCaptor<Path> checkoutPath = ArgumentCaptor.forClass(Path.class);
        verify(gitService).getOrCheckoutRepository(eq(templateUri), eq(templateUri), checkoutPath.capture(), eq(true), eq(DEFAULT_BRANCH), eq(false));
        assertThat(checkoutPath.getValue().getParent()).doesNotExist();
        verify(templateRepo).closeBeforeDelete();
    }

    @Test
    void revert_refusesToClobberManualProblemStatementEditsAfterTheAdaptation() throws Exception {
        when(exercise.getProblemStatement()).thenReturn("adapted statement", "manual statement");
        when(exercise.getTitle()).thenReturn("Adapted Title", "Adapted Title");
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isFalse();
        assertThat(result.get().revertedRepositories()).isEmpty();
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void revert_refusesBeforeRepositoryResetWhenPersistedMetadataChanged() throws Exception {
        when(persistenceService.canRestoreProblemStatementAndTitle(any(), any(), any(), any(), any())).thenReturn(false);
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.ADAPT, Map.of(RepositoryType.TEMPLATE, "sha-template"), Map.of(RepositoryType.TEMPLATE, "adapted-template"),
                "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user);

        assertThat(result).isPresent();
        assertThat(result.get().fullyReverted()).isFalse();
        assertThat(result.get().revertedRepositories()).isEmpty();
        verify(gitService, never()).getOrCheckoutRepository(any(), any(), any(Path.class), anyBoolean(), any(), anyBoolean());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void revert_whenOwnershipIsLostBeforeFirstReset_doesNotMutateAndKeepsBaseline() throws Exception {
        revertService.recordBaseline(exercise, "job-1", GenerationMode.GENERATE, preRunHeads(), postRunHeads(), "old statement", "Old Title");

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> false);

        assertThat(result).hasValueSatisfying(value -> {
            assertThat(value.fullyReverted()).isFalse();
            assertThat(value.revertedRepositories()).isEmpty();
        });
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");
        verify(gitService, never()).getOrCheckoutRepository(any(), any(), any(Path.class), anyBoolean(), any(), anyBoolean());
        verify(gitService, never()).resetToCommitAndForcePush(any(), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void revert_whenOwnershipIsLostAfterOneReset_returnsPartialAndKeepsBaseline() throws Exception {
        when(gitService.getLastCommitHash(templateUri, DEFAULT_BRANCH)).thenReturn("adapted-template");
        revertService.recordBaseline(exercise, "job-1", GenerationMode.GENERATE, Map.of(RepositoryType.TEMPLATE, "sha-template", RepositoryType.SOLUTION, "sha-solution"),
                Map.of(RepositoryType.TEMPLATE, "adapted-template", RepositoryType.SOLUTION, "adapted-solution"), "old statement", "Old Title");
        AtomicInteger ownershipChecks = new AtomicInteger();

        Optional<ExerciseGenerationRevertService.RevertResult> result = revertService.revert(exercise, user, () -> ownershipChecks.getAndIncrement() == 0);

        assertThat(result).hasValueSatisfying(value -> {
            assertThat(value.fullyReverted()).isFalse();
            assertThat(value.revertedRepositories()).containsExactly(RepositoryType.TEMPLATE);
        });
        assertThat(revertService.findRevertibleJobId(77L)).contains("job-1");
        verify(gitService).resetToCommitAndForcePush(templateRepo, "sha-template", "adapted-template", DEFAULT_BRANCH);
        verify(gitService, never()).resetToCommitAndForcePush(eq(solutionRepo), any(), any(), any());
        verify(persistenceService, never()).resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void revert_whenNoBaselineRetained_returnsEmpty() {
        assertThat(revertService.revert(exercise, user)).isEmpty();
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

    /** The pre-persist commit heads a full accepted adaptation of all three repositories hands back to {@code recordBaseline}. */
    private static Map<RepositoryType, String> preRunHeads() {
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        heads.put(RepositoryType.TEMPLATE, "sha-template");
        heads.put(RepositoryType.SOLUTION, "sha-solution");
        heads.put(RepositoryType.TESTS, "sha-tests");
        return heads;
    }
}
