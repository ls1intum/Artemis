package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.localci.service.ci.BulkTriggerThrottle;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localci.service.ci.SharedBuildTriggerData;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.ParticipationBuildTriggerDTO;
import de.tum.cit.aet.artemis.programming.dto.StartDateBuildCandidateDTO;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * Unit tests for triggering builds and telling the user what came of it.
 * <p>
 * Every path through these methods ends in a websocket message, and which message is sent is the whole point: a student
 * whose build could not be triggered has to be told, because nothing else will ever arrive for that submission and the
 * client would otherwise wait for a result forever. The other half is the resume path - a participation whose build plan
 * was cleaned up has to be rebuilt before it can be triggered, and skipping that turns a triggered build into a silent
 * failure.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingTriggerServiceTest {

    private static final long EXERCISE_ID = 7L;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    @Mock
    private ParticipationService participationService;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Mock
    private ProgrammingMessagingService programmingMessagingService;

    @Mock
    private SelectedBuildRunStatusService selectedBuildRunStatusService;

    @Mock
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    @Mock
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Mock
    private ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService;

    @Mock
    private ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    @Mock
    private BareGitRepositoryService bareGitRepositoryService;

    private ProgrammingTriggerService programmingTriggerService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        programmingTriggerService = new ProgrammingTriggerService(programmingSubmissionRepository, programmingExerciseRepository, Optional.of(continuousIntegrationTriggerService),
                participationService, programmingExerciseParticipationService, auditEventRepository, programmingExerciseStudentParticipationRepository, programmingMessagingService,
                templateProgrammingExerciseParticipationRepository, solutionProgrammingExerciseParticipationRepository, programmingExerciseTestCaseChangedService,
                programmingSubmissionMessagingService, selectedBuildRunStatusService, bareGitRepositoryService);
        // Batching is configured from properties, which a plain unit test does not read; without a size the batch pause
        // divides by zero. A large batch keeps every test below the point where the service would sleep.
        ReflectionTestUtils.setField(programmingTriggerService, "externalSystemRequestBatchSize", 100);
        ReflectionTestUtils.setField(programmingTriggerService, "externalSystemRequestBatchWaitingTime", 0);
        ReflectionTestUtils.setField(programmingTriggerService, "defaultBranch", "main");
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
    }

    private ProgrammingExerciseStudentParticipation studentParticipation(String buildPlanId, InitializationState state) {
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(100L);
        participation.setExercise(exercise);
        participation.setBuildPlanId(buildPlanId);
        participation.setInitializationState(state);
        return participation;
    }

    private static ProgrammingSubmission submissionOf(ProgrammingExerciseStudentParticipation participation) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setId(500L);
        submission.setCommitHash("abc123");
        submission.setParticipation(participation);
        return submission;
    }

    @Test
    void triggerBuildAndNotifyUser_triggersTheBuildAndTellsTheClientASubmissionExists() {
        var participation = studentParticipation("plan-1", InitializationState.INITIALIZED);
        var submission = submissionOf(participation);

        programmingTriggerService.triggerBuildAndNotifyUser(submission);

        verify(continuousIntegrationTriggerService).triggerBuild(participation);
        verify(programmingSubmissionMessagingService).notifyUserAboutSubmission(submission, EXERCISE_ID);
        // The participation is ready, so rebuilding it would throw away a working build plan.
        verify(participationService, never()).resumeProgrammingExercise(any());
    }

    @Test
    void triggerBuildAndNotifyUser_forAParticipationWhoseBuildPlanWasCleanedUp_resumesTheExerciseFirst() {
        // After the cleanup job removed the build plan, triggering without resuming would trigger a plan that no longer exists.
        var participation = studentParticipation(null, InitializationState.INITIALIZED);
        var submission = submissionOf(participation);

        programmingTriggerService.triggerBuildAndNotifyUser(submission);

        // The order is the point: triggering first would trigger the build plan that the resume is about to recreate.
        var inOrder = org.mockito.Mockito.inOrder(participationService, continuousIntegrationTriggerService);
        inOrder.verify(participationService).resumeProgrammingExercise(participation);
        inOrder.verify(continuousIntegrationTriggerService).triggerBuild(participation);
    }

    @Test
    void triggerBuildAndNotifyUser_forAParticipationThatWasNeverFinishedBeingSetUp_resumesTheExerciseFirst() {
        var participation = studentParticipation("plan-1", InitializationState.INACTIVE);
        var submission = submissionOf(participation);

        programmingTriggerService.triggerBuildAndNotifyUser(submission);

        verify(participationService).resumeProgrammingExercise(participation);
    }

    @Test
    void triggerBuildAndNotifyUser_whenTheBuildCannotBeTriggered_tellsTheStudentInsteadOfLeavingThemWaiting() {
        // Nothing else will ever arrive for this submission, so the error message is the only thing that ends the wait.
        var participation = studentParticipation("plan-1", InitializationState.INITIALIZED);
        var submission = submissionOf(participation);
        doThrow(new IllegalStateException("the CI system is unreachable")).when(continuousIntegrationTriggerService).triggerBuild(participation);

        programmingTriggerService.triggerBuildAndNotifyUser(submission);

        ArgumentCaptor<BuildTriggerWebsocketError> error = ArgumentCaptor.captor();
        verify(programmingSubmissionMessagingService).notifyUserAboutSubmissionError(eq(submission), error.capture());
        assertThat(error.getValue().getError()).isEqualTo("the CI system is unreachable");
        assertThat(error.getValue().getParticipationId()).isEqualTo(participation.getId());
        verify(programmingSubmissionMessagingService, never()).notifyUserAboutSubmission(any(), any());
    }

    @Test
    void triggerTemplateBuildAndNotifyUser_createsASubmissionForTheCommitAndTriggersIt() {
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setId(200L);
        templateParticipation.setProgrammingExercise(exercise);
        when(programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(EXERCISE_ID)).thenReturn(templateParticipation);
        when(programmingSubmissionRepository.saveAndFlush(any(ProgrammingSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        programmingTriggerService.triggerTemplateBuildAndNotifyUser(EXERCISE_ID, "deadbeef", SubmissionType.TEST, RepositoryType.TESTS);

        ArgumentCaptor<ProgrammingSubmission> saved = ArgumentCaptor.captor();
        verify(programmingSubmissionRepository).saveAndFlush(saved.capture());
        // The submission has to carry the commit that was pushed, otherwise the result cannot be attributed to it later.
        assertThat(saved.getValue().getCommitHash()).isEqualTo("deadbeef");
        assertThat(saved.getValue().getType()).isEqualTo(SubmissionType.TEST);
        assertThat(saved.getValue().isSubmitted()).isTrue();
        verify(continuousIntegrationTriggerService).triggerBuild(templateParticipation, "deadbeef", RepositoryType.TESTS);
        verify(programmingSubmissionMessagingService).notifyUserAboutSubmission(saved.getValue(), EXERCISE_ID);
    }

    @Test
    void triggerTemplateBuildAndNotifyUser_defaultsToAPushToTheTestRepository() {
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setId(200L);
        templateParticipation.setProgrammingExercise(exercise);
        when(programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(EXERCISE_ID)).thenReturn(templateParticipation);
        when(programmingSubmissionRepository.saveAndFlush(any(ProgrammingSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        programmingTriggerService.triggerTemplateBuildAndNotifyUser(EXERCISE_ID, "deadbeef", SubmissionType.TEST);

        verify(continuousIntegrationTriggerService).triggerBuild(templateParticipation, "deadbeef", RepositoryType.TESTS);
    }

    @Test
    void triggerTemplateBuildAndNotifyUser_whenTheBuildCannotBeTriggered_reportsTheErrorOnTheSubmission() {
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setId(200L);
        templateParticipation.setProgrammingExercise(exercise);
        when(programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(EXERCISE_ID)).thenReturn(templateParticipation);
        when(programmingSubmissionRepository.saveAndFlush(any(ProgrammingSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("no build agent")).when(continuousIntegrationTriggerService).triggerBuild(any(), any(), any());

        programmingTriggerService.triggerTemplateBuildAndNotifyUser(EXERCISE_ID, "deadbeef", SubmissionType.TEST, RepositoryType.TESTS);

        verify(programmingSubmissionMessagingService).notifyUserAboutSubmissionError(any(ProgrammingSubmission.class), any(BuildTriggerWebsocketError.class));
        verify(programmingSubmissionMessagingService, never()).notifyUserAboutSubmission(any(), any());
    }

    /**
     * A participation that already has a submission, which is the only kind that gets triggered.
     */
    private ProgrammingExerciseStudentParticipation participationWithSubmission(long id, ProgrammingExercise ofExercise) {
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(id);
        participation.setExercise(ofExercise);
        participation.setBuildPlanId("plan-" + id);
        participation.setInitializationState(InitializationState.INITIALIZED);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setId(id * 10);
        submission.setCommitHash("abc123");
        participation.addSubmission(submission);
        return participation;
    }

    @Test
    void triggerBuild_forAParticipationWithoutASubmission_doesNotTriggerAnything() {
        // There is nothing to build before the student has pushed anything, and a build without a commit produces a result
        // for work that does not exist.
        programmingTriggerService.triggerBuild(studentParticipation("plan-1", InitializationState.INITIALIZED));

        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
        verify(programmingSubmissionMessagingService, never()).notifyUserAboutSubmission(any(), any());
    }

    @Test
    void triggerBuild_forAParticipationWhoseBuildPlanWasCleanedUp_resumesTheExerciseFirst() {
        var participation = participationWithSubmission(1L, exercise);
        participation.setBuildPlanId(null);

        programmingTriggerService.triggerBuild(participation);

        verify(participationService).resumeProgrammingExercise(participation);
        verify(continuousIntegrationTriggerService).triggerBuild(eq(participation), anyBoolean(), any(SharedBuildTriggerData.class));
    }

    @Test
    void triggerBuild_whenTheBuildCannotBeTriggered_reportsTheErrorOnTheParticipation() {
        var participation = participationWithSubmission(1L, exercise);
        doThrow(new IllegalStateException("no build agent")).when(continuousIntegrationTriggerService).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));

        programmingTriggerService.triggerBuild(participation);

        ArgumentCaptor<BuildTriggerWebsocketError> error = ArgumentCaptor.captor();
        verify(programmingSubmissionMessagingService).notifyUserAboutSubmissionError(eq(participation), error.capture());
        assertThat(error.getValue().getParticipationId()).isEqualTo(1L);
    }

    @Test
    void triggerBuildForParticipations_triggersEveryParticipationThatHasSomethingToBuild() {
        programmingTriggerService.triggerBuildForParticipations(List.of(participationWithSubmission(1L, exercise), participationWithSubmission(2L, exercise)));

        verify(continuousIntegrationTriggerService, times(2)).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
    }

    @Test
    void triggerBuildForParticipations_skipsAnExerciseInWhichNobodySubmittedAnything() {
        // Resolving the shared trigger inputs reads the test repository, which is not worth doing for an exercise that
        // has nothing to build.
        var withoutSubmission = new ProgrammingExerciseStudentParticipation();
        withoutSubmission.setId(1L);
        withoutSubmission.setExercise(exercise);

        programmingTriggerService.triggerBuildForParticipations(List.of(withoutSubmission));

        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
        verify(continuousIntegrationTriggerService, never()).prepareSharedTriggerData(any());
    }

    @Test
    void triggerBuildForParticipations_skipsAParticipationThatCarriesNoExercise() {
        // Grouping is by exercise, so a participation without one cannot be triggered; it must not take the others down.
        var withoutExercise = new ProgrammingExerciseStudentParticipation();
        withoutExercise.setId(9L);

        programmingTriggerService.triggerBuildForParticipations(java.util.Arrays.asList(withoutExercise, participationWithSubmission(1L, exercise)));

        ArgumentCaptor<ProgrammingExerciseStudentParticipation> triggered = ArgumentCaptor.captor();
        verify(continuousIntegrationTriggerService).triggerBuild(triggered.capture(), anyBoolean(), any(SharedBuildTriggerData.class));
        assertThat(triggered.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void triggerBuildForParticipationData_forAParticipationThatHasToBeResumed_usesTheLoadedEntity() {
        // The resume path writes the participation back to the database, so it needs the real entity rather than the
        // detached projection the other participations are triggered from.
        var loaded = participationWithSubmission(1L, exercise);
        loaded.setBuildPlanId(null);
        when(programmingExerciseStudentParticipationRepository.findWithSubmissionsById(1L)).thenReturn(Optional.of(loaded));

        programmingTriggerService.triggerBuildForParticipationData(List.of(triggerData(1L, true)), exercise);

        verify(participationService).resumeProgrammingExercise(loaded);
        assertThat(loaded.getProgrammingExercise()).as("the loaded participation reuses the exercise the batch already holds").isSameAs(exercise);
    }

    @Test
    void triggerTemplateAndSolutionBuild_triggersBothSoTheExerciseIsCheckedFromBothEnds() {
        // The template must fail the tests and the solution must pass them; building only one of them leaves the exercise half checked.
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setId(200L);
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(300L);
        when(templateProgrammingExerciseParticipationRepository.findWithEagerSubmissionsByProgrammingExerciseId(EXERCISE_ID)).thenReturn(Optional.of(templateParticipation));
        when(solutionProgrammingExerciseParticipationRepository.findWithEagerSubmissionsByProgrammingExerciseId(EXERCISE_ID)).thenReturn(Optional.of(solutionParticipation));

        programmingTriggerService.triggerTemplateAndSolutionBuild(EXERCISE_ID);

        // Neither participation has a submission yet, so both are triggered directly rather than through a submission.
        verify(continuousIntegrationTriggerService).triggerBuild(templateParticipation);
        verify(continuousIntegrationTriggerService).triggerBuild(solutionParticipation);
    }

    /**
     * @param needsResume whether the projection describes a participation whose build plan has to be rebuilt first
     */
    private static ParticipationBuildTriggerDTO triggerData(long participationId, boolean needsResume) {
        return new ParticipationBuildTriggerDTO(participationId, "https://artemis.example.com/git/ABC/abc-student.git", needsResume ? null : "plan-" + participationId, "main",
                needsResume ? InitializationState.INACTIVE : InitializationState.INITIALIZED, null, false, 1L, "ge12abc", null, participationId * 10, SubmissionType.MANUAL, null,
                "abc123", true, false, false);
    }

    @Test
    void triggerInstructorBuildForExercise_bracketsTheRunWithTheMessagesTheInstructorWaitsOn() {
        // The instructor's page shows a running build run until the completion message arrives, so both have to be sent.
        when(programmingExerciseRepository.findWithAuxiliaryRepositoriesById(EXERCISE_ID)).thenReturn(Optional.of(exercise));
        when(programmingExerciseStudentParticipationRepository.findBuildTriggerDataByExerciseId(EXERCISE_ID)).thenReturn(List.of());

        programmingTriggerService.triggerInstructorBuildForExercise(EXERCISE_ID);

        // The order is the point: the instructor's page stops showing a running build run on the completion message, so the
        // exercise has to be marked as no longer needing a run before that message is sent.
        var inOrder = org.mockito.Mockito.inOrder(programmingMessagingService, programmingExerciseTestCaseChangedService);
        inOrder.verify(programmingMessagingService).notifyInstructorAboutStartedExerciseBuildRun(exercise);
        inOrder.verify(programmingExerciseTestCaseChangedService).setTestCasesChanged(EXERCISE_ID, false);
        inOrder.verify(programmingMessagingService).notifyInstructorAboutCompletedExerciseBuildRun(exercise);
    }

    @Test
    void triggerInstructorBuildForExercise_forAnExerciseThatDoesNotExist_isReported() {
        when(programmingExerciseRepository.findWithAuxiliaryRepositoriesById(EXERCISE_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingTriggerService.triggerInstructorBuildForExercise(EXERCISE_ID));

        verify(programmingMessagingService, never()).notifyInstructorAboutStartedExerciseBuildRun(any());
    }

    @Test
    void triggerBuildForParticipationData_forAnExerciseNobodyParticipatesIn_doesNothing() {
        programmingTriggerService.triggerBuildForParticipationData(List.of(), exercise);

        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
    }

    @Test
    void triggerBuildForParticipationData_triggersEveryParticipationOfTheExercise() {
        programmingTriggerService.triggerBuildForParticipationData(List.of(triggerData(1L, false), triggerData(2L, false)), exercise);

        ArgumentCaptor<ProgrammingExerciseStudentParticipation> triggered = ArgumentCaptor.captor();
        verify(continuousIntegrationTriggerService, times(2)).triggerBuild(triggered.capture(), anyBoolean(), any(SharedBuildTriggerData.class));
        assertThat(triggered.getAllValues()).extracting(ProgrammingExerciseStudentParticipation::getId).containsExactly(1L, 2L);
    }

    @Test
    void triggerBuildForParticipationData_skipsAParticipationThatHasBeenDeletedInTheMeantime() {
        // A build run over a thousand participations takes minutes, in which a participation can disappear; that must not
        // abort the run for everyone else.
        when(programmingExerciseStudentParticipationRepository.findWithSubmissionsById(1L)).thenReturn(Optional.empty());

        programmingTriggerService.triggerBuildForParticipationData(List.of(triggerData(1L, true), triggerData(2L, false)), exercise);

        ArgumentCaptor<ProgrammingExerciseStudentParticipation> triggered = ArgumentCaptor.captor();
        verify(continuousIntegrationTriggerService).triggerBuild(triggered.capture(), anyBoolean(), any(SharedBuildTriggerData.class));
        assertThat(triggered.getValue().getId()).isEqualTo(2L);
    }

    @Test
    void triggerBuildForParticipationData_waitsForRoomInTheBuildQueueBeforeEveryBuild() {
        // A bulk run that queues everything at once buries the build agents, so it asks for room before each build, not
        // once per run: the room another run or a student's push used up in the meantime must be noticed.
        BulkTriggerThrottle throttle = mock(BulkTriggerThrottle.class);
        when(continuousIntegrationTriggerService.createBulkTriggerThrottle()).thenReturn(throttle);

        programmingTriggerService.triggerBuildForParticipationData(List.of(triggerData(1L, false), triggerData(2L, false)), exercise);

        InOrder inOrder = inOrder(throttle, continuousIntegrationTriggerService);
        for (long participationId : new long[] { 1L, 2L }) {
            inOrder.verify(throttle).awaitCapacity();
            inOrder.verify(continuousIntegrationTriggerService).triggerBuild(argThat(triggered -> triggered.getId() == participationId), anyBoolean(),
                    any(SharedBuildTriggerData.class));
        }
    }

    @Test
    void triggerBuildForParticipationData_doesNotWaitForAParticipationThatHasNothingToBuild() {
        // Waiting for room in the queue for a build that is never queued only delays the participations behind it.
        BulkTriggerThrottle throttle = mock(BulkTriggerThrottle.class);
        when(continuousIntegrationTriggerService.createBulkTriggerThrottle()).thenReturn(throttle);
        var withoutSubmission = new ProgrammingExerciseStudentParticipation();
        withoutSubmission.setId(1L);
        withoutSubmission.setBuildPlanId(null);
        when(programmingExerciseStudentParticipationRepository.findWithSubmissionsById(1L)).thenReturn(Optional.of(withoutSubmission));

        programmingTriggerService.triggerBuildForParticipationData(List.of(triggerData(1L, true)), exercise);

        verify(throttle, never()).awaitCapacity();
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
    }

    @Test
    void triggerBuildForParticipations_neverWaitsForRoomInTheBuildQueue() {
        // This path also queues the builds at the start and the end of a student exam. They have to be queued at once, so
        // they do not take part in the wait that only a bulk run by an instructor accepts.
        programmingTriggerService.triggerBuildForParticipations(List.of(participationWithSubmission(1L, exercise), participationWithSubmission(2L, exercise)));

        verify(continuousIntegrationTriggerService, never()).createBulkTriggerThrottle();
        verify(continuousIntegrationTriggerService, times(2)).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
    }

    @Test
    void triggerInstructorBuildForParticipations_triggersEveryGivenParticipation() {
        programmingTriggerService.triggerInstructorBuildForParticipations(List.of(triggerData(1L, false), triggerData(2L, false)), exercise, "run-1");

        ArgumentCaptor<ProgrammingExerciseStudentParticipation> triggered = ArgumentCaptor.captor();
        verify(continuousIntegrationTriggerService, times(2)).triggerBuild(triggered.capture(), anyBoolean(), any(SharedBuildTriggerData.class));
        assertThat(triggered.getAllValues()).extracting(ProgrammingExerciseStudentParticipation::getId).containsExactly(1L, 2L);
    }

    @Test
    void triggerInstructorBuildForParticipations_tellsTheClientsWhichRunIsDoneOnceItQueuedItsBuilds() {
        // The retry action of the client stays busy until its own run has queued the last build, which it learns from this message.
        programmingTriggerService.triggerInstructorBuildForParticipations(List.of(triggerData(1L, false)), exercise, "run-1");

        // The end is recorded before the message, so a client that gets the message and asks for the status finds the run ended.
        InOrder inOrder = inOrder(programmingMessagingService, continuousIntegrationTriggerService, selectedBuildRunStatusService);
        inOrder.verify(continuousIntegrationTriggerService).triggerBuild(any(), anyBoolean(), any(SharedBuildTriggerData.class));
        inOrder.verify(selectedBuildRunStatusService).markEnded("run-1");
        inOrder.verify(programmingMessagingService).notifyAboutCompletedSelectedBuildRun(exercise, "run-1");
        // The run of all builds sends the notification to the instructors, a rebuild of a few participations does not.
        verify(programmingMessagingService, never()).notifyInstructorAboutStartedExerciseBuildRun(any());
        verify(programmingMessagingService, never()).notifyInstructorAboutCompletedExerciseBuildRun(any());
    }

    @Test
    void triggerInstructorBuildForParticipations_namesEachRunInItsOwnMessage() {
        programmingTriggerService.triggerInstructorBuildForParticipations(List.of(triggerData(1L, false)), exercise, "run-1");
        programmingTriggerService.triggerInstructorBuildForParticipations(List.of(triggerData(2L, false)), exercise, "run-2");

        verify(programmingMessagingService).notifyAboutCompletedSelectedBuildRun(exercise, "run-1");
        verify(programmingMessagingService).notifyAboutCompletedSelectedBuildRun(exercise, "run-2");
    }

    @Test
    void triggerInstructorBuildForParticipations_tellsTheClientsThatTheRunIsDoneAlsoWhenItFails() {
        BulkTriggerThrottle throttle = mock(BulkTriggerThrottle.class);
        when(continuousIntegrationTriggerService.createBulkTriggerThrottle()).thenReturn(throttle);
        doThrow(new IllegalStateException("the queue cannot be read")).when(throttle).awaitCapacity();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> programmingTriggerService.triggerInstructorBuildForParticipations(List.of(triggerData(1L, false)), exercise, "run-1"));

        verify(programmingMessagingService).notifyAboutCompletedSelectedBuildRun(exercise, "run-1");
        verify(selectedBuildRunStatusService).markEnded("run-1");
    }

    @Test
    void triggerTemplateAndSolutionBuild_forAnExerciseWithoutThoseParticipations_doesNothing() {
        when(templateProgrammingExerciseParticipationRepository.findWithEagerSubmissionsByProgrammingExerciseId(EXERCISE_ID)).thenReturn(Optional.empty());
        when(solutionProgrammingExerciseParticipationRepository.findWithEagerSubmissionsByProgrammingExerciseId(EXERCISE_ID)).thenReturn(Optional.empty());

        programmingTriggerService.triggerTemplateAndSolutionBuild(EXERCISE_ID);

        verify(continuousIntegrationTriggerService, never()).triggerBuild(any());
    }

    private ProgrammingExerciseStudentParticipation participationWithRepository(long id, String login) {
        ProgrammingExerciseStudentParticipation participation = studentParticipation("plan-" + id, InitializationState.INITIALIZED);
        participation.setId(id);
        participation.setRepositoryUri("http://localhost:8080/git/PROJ/proj-" + login + ".git");
        participation.setBranch("main");
        return participation;
    }

    private static StartDateBuildCandidateDTO candidateOf(ProgrammingExerciseStudentParticipation participation, String latestCommitHash) {
        return new StartDateBuildCandidateDTO(participation.getId(), participation.getRepositoryUri(), participation.getBranch(), latestCommitHash);
    }

    private void allowRepositoryAccessBeforeStartDate(ZonedDateTime startDate) {
        exercise.setAllowRepositoryAccessBeforeStartDate(true);
        exercise.setReleaseDate(startDate.minusDays(1));
        exercise.setStartDate(startDate);
        when(programmingExerciseRepository.findWithAuxiliaryRepositoriesById(EXERCISE_ID)).thenReturn(Optional.of(exercise));
    }

    /**
     * Lets exactly the given participation be loaded, as the only one whose repository changed.
     */
    private void allowLoadingOf(ProgrammingExerciseStudentParticipation participation) {
        when(programmingExerciseStudentParticipationRepository.findWithParticipantByIds(List.of(participation.getId()))).thenReturn(List.of(participation));
    }

    /**
     * Lets the given participation be loaded and a submission for it be saved, so the start date build can create one.
     */
    private void allowSubmissionFor(ProgrammingExerciseStudentParticipation participation) {
        allowLoadingOf(participation);
        when(programmingSubmissionRepository.saveAndFlush(any(ProgrammingSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_buildsTheHeadOfEveryRepositoryThatReceivedAPush() {
        ZonedDateTime startDate = ZonedDateTime.now().minusMinutes(1);
        allowRepositoryAccessBeforeStartDate(startDate);
        var pushed = participationWithRepository(1L, "student1");
        var untouched = participationWithRepository(2L, "student2");
        when(programmingExerciseStudentParticipationRepository.findStartDateBuildCandidatesByExerciseId(EXERCISE_ID, exercise.getStartDate()))
                .thenReturn(List.of(candidateOf(pushed, null), candidateOf(untouched, null)));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(pushed.getVcsRepositoryUri(), "main")).thenReturn(Optional.of("cafebabe"));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(untouched.getVcsRepositoryUri(), "main")).thenReturn(Optional.empty());
        allowSubmissionFor(pushed);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        ArgumentCaptor<ProgrammingSubmission> submission = ArgumentCaptor.captor();
        verify(programmingSubmissionRepository).saveAndFlush(submission.capture());
        assertThat(submission.getValue().getCommitHash()).isEqualTo("cafebabe");
        assertThat(submission.getValue().getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.getValue().getParticipation()).isEqualTo(pushed);
        // the commit was pushed before the start date, so a run that waited until after the due date must not make the result unrated
        assertThat(submission.getValue().getSubmissionDate()).isEqualTo(startDate);
        verify(continuousIntegrationTriggerService).triggerBuild(eq(pushed), eq("cafebabe"), eq(RepositoryType.USER), eq(true), any());
        verify(programmingSubmissionMessagingService).notifyUserAboutSubmission(submission.getValue(), EXERCISE_ID);
        // a repository that still holds only the setup commit has nothing to build
        verify(continuousIntegrationTriggerService, never()).triggerBuild(eq(untouched), any(), any(), anyBoolean(), any());
        // only the changed participation is loaded, in one query
        verify(programmingExerciseStudentParticipationRepository).findWithParticipantByIds(List.of(pushed.getId()));
        verify(programmingExerciseStudentParticipationRepository, never()).findById(anyLong());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_buildsTheCommitItCreatedTheSubmissionFor() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        var pushed = participationWithRepository(1L, "student1");
        when(programmingExerciseStudentParticipationRepository.findStartDateBuildCandidatesByExerciseId(EXERCISE_ID, exercise.getStartDate()))
                .thenReturn(List.of(candidateOf(pushed, null)));
        // the student pushed again while the run waited for room in the build queue
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(pushed.getVcsRepositoryUri(), "main")).thenReturn(Optional.of("first"), Optional.of("second"));
        allowSubmissionFor(pushed);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        ArgumentCaptor<ProgrammingSubmission> submission = ArgumentCaptor.captor();
        verify(programmingSubmissionRepository).saveAndFlush(submission.capture());
        assertThat(submission.getValue().getCommitHash()).isEqualTo("second");
        verify(continuousIntegrationTriggerService).triggerBuild(eq(pushed), eq("second"), eq(RepositoryType.USER), eq(true), any());
        // the path that reads the head of the repository at the time of the trigger could build a commit the submission does not point to
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyBoolean(), any());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_skipsAParticipationWhoseHeadGotASubmissionMeanwhile() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        var pushed = participationWithRepository(1L, "student1");
        when(programmingExerciseStudentParticipationRepository.findStartDateBuildCandidatesByExerciseId(EXERCISE_ID, exercise.getStartDate()))
                .thenReturn(List.of(candidateOf(pushed, null)));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(pushed.getVcsRepositoryUri(), "main")).thenReturn(Optional.of("cafebabe"));
        // a push after the start date or an overlapping run created the submission while this run waited
        when(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(pushed.getId(), "cafebabe")).thenReturn(true);
        allowLoadingOf(pushed);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(programmingSubmissionRepository, never()).saveAndFlush(any());
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_buildsAStudentCommitAfterASubmissionOfAnInstructor() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        var helped = participationWithRepository(1L, "student1");
        var built = participationWithRepository(2L, "student2");
        // An instructor's push before the start date created a submission, and the student pushed after it. The second participation's head already has its submission.
        when(programmingExerciseStudentParticipationRepository.findStartDateBuildCandidatesByExerciseId(EXERCISE_ID, exercise.getStartDate()))
                .thenReturn(List.of(candidateOf(helped, "instructor"), candidateOf(built, "student")));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(helped.getVcsRepositoryUri(), "main")).thenReturn(Optional.of("student"));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(built.getVcsRepositoryUri(), "main")).thenReturn(Optional.of("student"));
        allowSubmissionFor(helped);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(continuousIntegrationTriggerService).triggerBuild(eq(helped), eq("student"), eq(RepositoryType.USER), eq(true), any());
        verify(continuousIntegrationTriggerService, never()).triggerBuild(eq(built), any(), any(), anyBoolean(), any());
        verify(programmingSubmissionRepository, never()).existsByParticipationIdAndCommitHash(eq(built.getId()), any());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_whileARunForTheExerciseIsInProgress_buildsNothing() {
        @SuppressWarnings("unchecked")
        var runningExercises = (Set<Long>) ReflectionTestUtils.getField(programmingTriggerService, "exercisesWithRunningStartDateBuild");
        runningExercises.add(EXERCISE_ID);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(programmingExerciseRepository, never()).findWithAuxiliaryRepositoriesById(anyLong());
        // the run in progress still owns the exercise
        assertThat(runningExercises).contains(EXERCISE_ID);
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_carriesOnAfterAParticipationFails() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        var broken = participationWithRepository(1L, "student1");
        var pushed = participationWithRepository(2L, "student2");
        when(programmingExerciseStudentParticipationRepository.findStartDateBuildCandidatesByExerciseId(EXERCISE_ID, exercise.getStartDate()))
                .thenReturn(List.of(candidateOf(broken, null), candidateOf(pushed, null)));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(broken.getVcsRepositoryUri(), "main")).thenThrow(new IllegalStateException("unreadable"));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(pushed.getVcsRepositoryUri(), "main")).thenReturn(Optional.of("cafebabe"));
        allowSubmissionFor(pushed);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(continuousIntegrationTriggerService).triggerBuild(eq(pushed), eq("cafebabe"), eq(RepositoryType.USER), eq(true), any());
        // a failed run does not keep the exercise blocked for the next one
        assertThat((Set<?>) ReflectionTestUtils.getField(programmingTriggerService, "exercisesWithRunningStartDateBuild")).isEmpty();
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_withoutChangedRepositories_loadsNoParticipation() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        var untouched = participationWithRepository(1L, "student1");
        when(programmingExerciseStudentParticipationRepository.findStartDateBuildCandidatesByExerciseId(EXERCISE_ID, exercise.getStartDate()))
                .thenReturn(List.of(candidateOf(untouched, null)));
        when(bareGitRepositoryService.findHeadCommitHashIfChangedSinceSetup(untouched.getVcsRepositoryUri(), "main")).thenReturn(Optional.empty());

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(programmingExerciseStudentParticipationRepository, never()).findWithParticipantByIds(any());
        verify(programmingSubmissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_beforeTheStartDate_buildsNothing() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().plusHours(1));

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(programmingExerciseStudentParticipationRepository, never()).findStartDateBuildCandidatesByExerciseId(anyLong(), any());
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyBoolean(), any());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_withoutTheOption_buildsNothing() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        exercise.setAllowRepositoryAccessBeforeStartDate(false);

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(programmingExerciseStudentParticipationRepository, never()).findStartDateBuildCandidatesByExerciseId(anyLong(), any());
    }

    @Test
    void triggerBuildsForChangesBeforeStartDate_forAnExamExercise_buildsNothing() {
        allowRepositoryAccessBeforeStartDate(ZonedDateTime.now().minusMinutes(1));
        exercise.setExerciseGroup(new ExerciseGroup());

        programmingTriggerService.triggerBuildsForChangesBeforeStartDate(EXERCISE_ID);

        verify(programmingExerciseStudentParticipationRepository, never()).findStartDateBuildCandidatesByExerciseId(anyLong(), any());
    }
}
