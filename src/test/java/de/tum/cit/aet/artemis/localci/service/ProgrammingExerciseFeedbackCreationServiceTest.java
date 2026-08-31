package de.tum.cit.aet.artemis.localci.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.service.FeedbackMessageService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingExerciseFeedbackCreationServiceTest {

    @Test
    void skipsTestCaseUpdatesFromStaleTestsCommit() {
        Harness harness = harness("stale-tests-commit");
        when(harness.gitService().getLastCommitHash(harness.testsUri(), "main")).thenReturn("current-tests-commit");

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(harness.buildResult(), harness.exercise());

        verify(harness.service(), never()).generateTestCasesFromBuildResult(harness.buildResult(), harness.exercise());
        verifyNoInteractions(harness.testCaseRepository(), harness.websocketMessagingService(), harness.taskService());
    }

    @Test
    void appliesTestCaseUpdatesFromCurrentTestsCommit() {
        Harness harness = harness("current-tests-commit");
        when(harness.gitService().getLastCommitHash(harness.testsUri(), "main")).thenReturn("current-tests-commit");

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(harness.buildResult(), harness.exercise());

        verify(harness.service()).generateTestCasesFromBuildResult(harness.buildResult(), harness.exercise());
    }

    @Test
    void appliesTestCaseUpdatesFromCurrentTestsCommitOnCustomBuildBranch() {
        Harness harness = harness("current-tests-commit");
        when(harness.exerciseRepository().findBranchByExerciseId(42L)).thenReturn("custom-build-branch");
        when(harness.gitService().getLastCommitHash(harness.testsUri())).thenReturn("symbolic-head-commit");
        when(harness.gitService().getLastCommitHash(harness.testsUri(), "custom-build-branch")).thenReturn("current-tests-commit");

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(harness.buildResult(), harness.exercise());

        verify(harness.gitService()).getLastCommitHash(harness.testsUri(), "custom-build-branch");
        verify(harness.gitService(), never()).getLastCommitHash(harness.testsUri());
        verify(harness.service()).generateTestCasesFromBuildResult(harness.buildResult(), harness.exercise());
    }

    @Test
    void verifiesTestsCommitUsingStoredBuildBranchWithoutLoadingBuildConfig() {
        Harness harness = harness("current-tests-commit");
        when(harness.buildResult().assignmentRepoBranchName()).thenThrow(new AssertionError("assignment branch must not be loaded"));
        when(harness.exercise().getBuildConfig()).thenThrow(new AssertionError("build config must not be loaded"));
        when(harness.gitService().getLastCommitHash(harness.testsUri(), "main")).thenReturn("current-tests-commit");

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(harness.buildResult(), harness.exercise());

        verify(harness.exerciseRepository()).findBranchByExerciseId(42L);
        verify(harness.gitService()).getLastCommitHash(harness.testsUri(), "main");
        verify(harness.service()).generateTestCasesFromBuildResult(harness.buildResult(), harness.exercise());
    }

    @Test
    void acceptsLegacyResultWithoutTestsCommit() {
        Harness harness = harness((String) null);

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(harness.buildResult(), harness.exercise());

        verifyNoInteractions(harness.gitService());
        verify(harness.service()).generateTestCasesFromBuildResult(harness.buildResult(), harness.exercise());
    }

    @Test
    void skipsTestCaseUpdatesWhenCurrentTestsCommitCannotBeRead() {
        Harness harness = harness("tests-commit");
        when(harness.gitService().getLastCommitHash(harness.testsUri(), "main")).thenThrow(new IllegalStateException("VCS unavailable"));

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(harness.buildResult(), harness.exercise());

        verify(harness.service(), never()).generateTestCasesFromBuildResult(harness.buildResult(), harness.exercise());
    }

    @Test
    void doesNotApplyLocalCiCommitFenceToExternalCiResults() {
        BuildResultNotification externalResult = mock();
        when(externalResult.testsRepoCommitHash()).thenReturn("external-tests-commit");
        Harness harness = harness(externalResult);

        harness.service().extractTestCasesFromResultAndBroadcastUpdates(externalResult, harness.exercise());

        verifyNoInteractions(harness.gitService());
        verify(harness.service()).generateTestCasesFromBuildResult(externalResult, harness.exercise());
    }

    private static Harness harness(String testsCommit) {
        BuildResultNotification buildResult = mock(BuildResult.class);
        when(buildResult.testsRepoCommitHash()).thenReturn(testsCommit);
        return harness(buildResult);
    }

    private static Harness harness(BuildResultNotification buildResult) {
        ProgrammingExerciseTestCaseTestRepository testCaseRepository = mock();
        WebsocketMessagingService websocketMessagingService = mock();
        ProgrammingExerciseTaskService taskService = mock();
        ProgrammingExerciseTestRepository exerciseRepository = mock();
        GitService gitService = mock();
        ProgrammingExerciseFeedbackCreationService service = spy(new ProgrammingExerciseFeedbackCreationService(testCaseRepository, websocketMessagingService, taskService,
                exerciseRepository, mock(StaticCodeAnalysisCategoryRepository.class), gitService, mock(FeedbackMessageService.class)));
        ProgrammingExercise exercise = mock();
        LocalVCRepositoryUri testsUri = mock();
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getRepositoryURI(RepositoryType.TESTS)).thenReturn(testsUri);
        when(exerciseRepository.findBranchByExerciseId(42L)).thenReturn("main");
        doReturn(false).when(service).generateTestCasesFromBuildResult(buildResult, exercise);
        return new Harness(service, buildResult, exercise, testsUri, gitService, testCaseRepository, websocketMessagingService, taskService, exerciseRepository);
    }

    private record Harness(ProgrammingExerciseFeedbackCreationService service, BuildResultNotification buildResult, ProgrammingExercise exercise, LocalVCRepositoryUri testsUri,
            GitService gitService, ProgrammingExerciseTestCaseTestRepository testCaseRepository, WebsocketMessagingService websocketMessagingService,
            ProgrammingExerciseTaskService taskService, ProgrammingExerciseTestRepository exerciseRepository) {
    }
}
