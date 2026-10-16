package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.service.FeedbackMessageService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingExerciseResultRevisionTest {

    private final ProgrammingExerciseTestRepository exercises = mock(ProgrammingExerciseTestRepository.class);

    private final ProgrammingExerciseBuildConfigRepository configs = mock(ProgrammingExerciseBuildConfigRepository.class);

    private final ProgrammingExerciseTestCaseTestRepository cases = mock(ProgrammingExerciseTestCaseTestRepository.class);

    private final ProgrammingExerciseTaskService tasks = mock(ProgrammingExerciseTaskService.class);

    private final GitService git = mock(GitService.class);

    private final LocalVCRepositoryUri solutionUri = mock(LocalVCRepositoryUri.class);

    private final LocalVCRepositoryUri testsUri = mock(LocalVCRepositoryUri.class);

    private final GenerationLiveSaveService saves = new GenerationLiveSaveService(new LocalDataProviderService(), true);

    private final AtomicReference<String> solutionHead = new AtomicReference<>("generated-solution");

    private final AtomicReference<String> testsHead = new AtomicReference<>("generated-tests");

    private ProgrammingExerciseFeedbackCreationService service;

    private ProgrammingExercise current;

    private ProgrammingExercise stale;

    private ProgrammingExerciseTestCase generated;

    private ProgrammingExerciseTestCase old;

    @BeforeEach
    void setUp() {
        current = mock(ProgrammingExercise.class);
        stale = new ProgrammingExercise();
        stale.setId(1L);
        stale.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        stale.setProblemStatement("stale instructions");
        when(current.getId()).thenReturn(1L);
        when(current.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(current.getProblemStatement()).thenReturn("Instructor's latest instructions");
        when(current.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);
        when(current.getRepositoryURI(RepositoryType.TESTS)).thenReturn(testsUri);
        when(exercises.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(current);
        ProgrammingExerciseBuildConfig config = new ProgrammingExerciseBuildConfig();
        config.setBranch("main");
        when(configs.getProgrammingExerciseBuildConfigElseThrow(1L)).thenReturn(config);
        when(git.getLastCommitHash(solutionUri, "main")).thenAnswer(_ -> solutionHead.get());
        when(git.getLastCommitHash(testsUri, "main")).thenAnswer(_ -> testsHead.get());
        generated = new ProgrammingExerciseTestCase().testName("generatedTest").active(true).visibility(Visibility.ALWAYS).weight(3.0).exercise(current);
        generated.setId(1L);
        old = new ProgrammingExerciseTestCase().testName("oldTest").active(false).visibility(Visibility.ALWAYS).weight(1.0).exercise(current);
        old.setId(2L);
        when(cases.findByExerciseId(1L)).thenReturn(Set.of(generated, old));
        doAnswer(call -> {
            Set<ProgrammingExerciseTestCase> savedCases = call.getArgument(0);
            for (ProgrammingExerciseTestCase saved : savedCases) {
                if (generated.getId().equals(saved.getId())) {
                    generated.setActive(saved.isActive());
                }
                if (old.getId().equals(saved.getId())) {
                    old.setActive(saved.isActive());
                }
            }
            return List.of();
        }).when(cases).saveAll(any());
        service = new ProgrammingExerciseFeedbackCreationService(cases, mock(WebsocketMessagingService.class), tasks, exercises, mock(StaticCodeAnalysisCategoryRepository.class),
                mock(FeedbackMessageService.class), Optional.of(new HyperionExerciseMutationApi(saves)), git, configs, "main");
    }

    private BuildResult result(String solution, String tests, String test) {
        var jobs = test == null ? List.<LocalCIJobDTO>of() : List.of(new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO(test, List.of()))));
        return new BuildResult("main", solution, tests, false, null, jobs, List.of(), List.of(), false, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void delayedOldSolutionResultCannotReplaceSavedOrUndoneSuite(boolean undo) {
        if (undo) {
            solutionHead.set("restored-solution");
            testsHead.set("restored-tests");
        }
        service.extractTestCasesFromResultAndBroadcastUpdates(result("old-solution", "old-tests", "oldTest"), stale);
        service.extractTestCasesFromResultAndBroadcastUpdates(result("old-solution", testsHead.get(), null), stale);
        assertThat(generated.isActive()).isTrue();
        assertThat(old.isActive()).isFalse();
        assertThat(generated.getWeight()).isEqualTo(3.0);
        verifyNoInteractions(tasks);
        verify(cases, never()).saveAll(any());
        verify(exercises, never()).save(any());
    }

    @org.junit.jupiter.api.Test
    void ordinaryWriterDefersCanonicalDiscoveryButNextCurrentBuildReconciles() {
        try (var writer = saves.reserveWrite(1L)) {
            service.extractTestCasesFromResultAndBroadcastUpdates(result(solutionHead.get(), testsHead.get(), "oldTest"), stale);
            assertThat(generated.isActive()).isTrue();
            assertThat(old.isActive()).isFalse();
            verifyNoInteractions(tasks);
            writer.verify();
        }
        service.extractTestCasesFromResultAndBroadcastUpdates(result(solutionHead.get(), testsHead.get(), "oldTest"), stale);
        assertThat(old.isActive()).isTrue();
        assertThat(generated.isActive()).isFalse();
        verify(exercises).save(same(current));
    }

    @org.junit.jupiter.api.Test
    void releasedExerciseRetainsOrdinaryDiscoveryWithoutCoordinationOrHeadReads() {
        stale.setReleaseDate(ZonedDateTime.now().minusDays(1));
        try (var writer = saves.reserveWrite(1L)) {
            service.extractTestCasesFromResultAndBroadcastUpdates(result("old-solution", "old-tests", "oldTest"), stale);
            writer.verify();
        }
        assertThat(old.isActive()).isTrue();
        verifyNoInteractions(git, configs);
        verify(exercises).save(same(stale));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedExpectedBuildDoesNotConsumeSaveOrUndoPermit(boolean undo) {
        if (undo) {
            solutionHead.set("restored-solution");
            testsHead.set("restored-tests");
        }
        try (var writer = saves.reserveWrite(1L)) {
            saves.expectTestCaseSynchronization(1L, solutionHead.get(), testsHead.get());
            var failed = new BuildResult("main", solutionHead.get(), testsHead.get(), false, null, List.of(), List.of(), List.of(), false, 1);
            service.extractTestCasesFromResultAndBroadcastUpdates(failed, stale);
            assertThat(saves.testCaseSynchronizationCompleted(1L, solutionHead.get(), testsHead.get())).isFalse();
            assertThat(generated.isActive()).isTrue();
            assertThat(old.isActive()).isFalse();
            verify(cases, never()).saveAll(any());
            verify(exercises, never()).save(any());
            verifyNoInteractions(tasks);
            writer.verify();
            service.extractTestCasesFromResultAndBroadcastUpdates(result(solutionHead.get(), testsHead.get(), "oldTest"), stale);
            assertThat(saves.testCaseSynchronizationCompleted(1L, solutionHead.get(), testsHead.get())).isTrue();
            assertThat(old.isActive()).isTrue();
            assertThat(generated.isActive()).isFalse();
            verify(exercises).save(same(current));
            writer.verify();
        }
    }

    @ParameterizedTest
    @CsvSource({ "true,1,true,true", "true,0,true,true", "false,1,false,true", "true,0,false,false", "false,,false,true" })
    void expectedSynchronizationRequiresCleanExitAndPassingReports(boolean rawSuccess, Integer exitCode, boolean failedTests, boolean passingTests) {
        var failures = failedTests ? List.of(new LocalCITestJobDTO("failedTest", List.of())) : List.<LocalCITestJobDTO>of();
        var passes = passingTests ? List.of(new LocalCITestJobDTO("oldTest", List.of())) : List.<LocalCITestJobDTO>of();
        var build = new BuildResult("main", solutionHead.get(), testsHead.get(), rawSuccess, null, List.of(new LocalCIJobDTO(failures, passes)), List.of(), List.of(), false,
                exitCode);
        try (var writer = saves.reserveWrite(1L)) {
            saves.expectTestCaseSynchronization(1L, solutionHead.get(), testsHead.get());
            service.extractTestCasesFromResultAndBroadcastUpdates(build, stale);
            assertThat(saves.testCaseSynchronizationCompleted(1L, solutionHead.get(), testsHead.get())).isFalse();
            assertThat(generated.isActive()).isTrue();
            assertThat(old.isActive()).isFalse();
            verifyNoInteractions(tasks);
            verify(cases, never()).saveAll(any());
            verify(exercises, never()).save(any());
            writer.verify();
        }
    }

    @org.junit.jupiter.api.Test
    void parserShapedAllPassingReportCompletesExpectedSynchronizationDespiteLegacyFalseFlag() {
        var tests = java.util.stream.IntStream.range(0, 15).mapToObj(i -> new LocalCITestJobDTO("reportedTest" + i, List.of())).toList();
        var build = new BuildResult("main", solutionHead.get(), testsHead.get(), false, null, List.of(new LocalCIJobDTO(List.of(), tests)), List.of(), List.of(), false, 0);
        try (var writer = saves.reserveWrite(1L)) {
            saves.expectTestCaseSynchronization(1L, solutionHead.get(), testsHead.get());
            service.extractTestCasesFromResultAndBroadcastUpdates(build, stale);
            assertThat(saves.testCaseSynchronizationCompleted(1L, solutionHead.get(), testsHead.get())).isTrue();
            verify(exercises).save(same(current));
            writer.verify();
        }
    }

    @org.junit.jupiter.api.Test
    void failedOrdinaryDraftBuildStillDiscoversReportedTests() {
        var reported = result(solutionHead.get(), testsHead.get(), "oldTest");
        var failed = new BuildResult("main", solutionHead.get(), testsHead.get(), false, null, reported.jobs(), List.of(), List.of(), false, 1);
        service.extractTestCasesFromResultAndBroadcastUpdates(failed, stale);
        assertThat(old.isActive()).isTrue();
        assertThat(generated.isActive()).isFalse();
        verify(exercises).save(same(current));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void expectedSaveAndUndoResultsCanDiscoverCurrentTestsUsingFreshMetadata(boolean undo) {
        if (undo) {
            solutionHead.set("restored-solution");
            testsHead.set("restored-tests");
        }
        try (var writer = saves.reserveWrite(1L)) {
            saves.expectTestCaseSynchronization(1L, solutionHead.get(), testsHead.get());
            service.extractTestCasesFromResultAndBroadcastUpdates(result(solutionHead.get(), testsHead.get(), "oldTest"), stale);
            assertThat(saves.testCaseSynchronizationCompleted(1L, solutionHead.get(), testsHead.get())).isTrue();
            writer.verify();
        }
        assertThat(old.isActive()).isTrue();
        assertThat(generated.isActive()).isFalse();
        verify(tasks).updateTasksFromProblemStatement(same(current));
        verify(tasks).replaceTestNamesWithIds(same(current));
        verify(exercises).save(same(current));
        assertThat(current.getProblemStatement()).isEqualTo("Instructor's latest instructions");
        verify(exercises, never()).save(same(stale));
    }
}
