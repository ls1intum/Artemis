package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.iris.domain.askuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisJobExpiredEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisAskUserService;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

class PyrisStatusUpdateServiceTest {

    private PyrisJobService pyrisJobService;

    private IrisChatSessionService irisChatSessionService;

    private IrisAskUserService irisAskUserService;

    private IrisCompetencyGenerationService competencyGenerationService;

    private IrisTutorSuggestionSessionService irisTutorSuggestionSessionService;

    private AutonomousTutorService autonomousTutorService;

    private ProcessingStateCallbackApi processingStateCallbackApi;

    private IrisWebsocketService irisWebsocketService;

    private PyrisStatusUpdateService service;

    @BeforeEach
    void setUp() {
        pyrisJobService = mock(PyrisJobService.class);
        irisChatSessionService = mock(IrisChatSessionService.class);
        irisAskUserService = mock(IrisAskUserService.class);
        competencyGenerationService = mock(IrisCompetencyGenerationService.class);
        irisTutorSuggestionSessionService = mock(IrisTutorSuggestionSessionService.class);
        autonomousTutorService = mock(AutonomousTutorService.class);
        processingStateCallbackApi = mock(ProcessingStateCallbackApi.class);
        irisWebsocketService = mock(IrisWebsocketService.class);
        service = new PyrisStatusUpdateService(pyrisJobService, irisChatSessionService, irisAskUserService, competencyGenerationService, irisTutorSuggestionSessionService,
                autonomousTutorService, Optional.of(processingStateCallbackApi), irisWebsocketService);
    }

    @ParameterizedTest
    @EnumSource(PyrisRunState.class)
    void chatJobLifecycleUsesRunState(PyrisRunState runState) {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO(null, runState, null, null, null, null, null, null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate, null)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisChatSessionService).handleStatusUpdate(job, statusUpdate, null);
        verifyLifecycle(job, runState);
    }

    @Test
    void chatJobResultWithAskUserEventIsHandledByAskUserService() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("result", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.NEXT_QUESTION.name(), null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate, statusUpdate.event())).thenReturn(job);
        when(pyrisJobService.removeJob(job)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        var inOrder = inOrder(irisChatSessionService, irisAskUserService, pyrisJobService);
        inOrder.verify(irisChatSessionService).handleStatusUpdate(job, statusUpdate, statusUpdate.event());
        inOrder.verify(pyrisJobService).removeJob(job);
        inOrder.verify(irisAskUserService).handleStatusUpdate(job, statusUpdate);
    }

    @Test
    void chatJobResultWithAskUserEventButJobAlreadyRemovedDoesNotTriggerAskUserService() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("result", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.NEXT_QUESTION.name(), null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate, statusUpdate.event())).thenReturn(job);
        when(pyrisJobService.removeJob(job)).thenReturn(null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(pyrisJobService).removeJob(job);
        verify(irisAskUserService, never()).handleStatusUpdate(job, statusUpdate);
    }

    @Test
    void duplicateChatJobResultWithAskUserEventDoesNotTriggerAskUserServiceAgain() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("result", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, null, null, null,
                IrisPipeEvent.USER_STARTS_QUIZ.name(), null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate, statusUpdate.event())).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisChatSessionService).handleStatusUpdate(job, statusUpdate, statusUpdate.event());
        verify(pyrisJobService).removeJob(job);
        verify(irisAskUserService, never()).handleStatusUpdate(job, statusUpdate);
    }

    @Test
    void intermediateChatJobResultWithAskUserEventDoesNotTriggerAskUserService() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("intermediate", PyrisRunState.RUNNING, null, null, null, null, null, null, null, null, null, null, false,
                IrisPipeEvent.USER_STARTS_QUIZ.name(), null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate, statusUpdate.event())).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisChatSessionService).handleStatusUpdate(job, statusUpdate, statusUpdate.event());
        verify(pyrisJobService).updateJob(job);
        verify(irisAskUserService, never()).handleStatusUpdate(job, statusUpdate);
    }

    @Test
    void failedAskUserChatJobResetsAskUserPipeline() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        var error = new PyrisStatusErrorDTO("artemisApp.iris.error.internal", null);
        var statusUpdate = new PyrisChatStatusUpdateDTO(null, PyrisRunState.FAILED, error, null, null, null, null, null);
        var expectedStatusUpdate = new PyrisChatStatusUpdateDTO(null, PyrisRunState.FAILED, new PyrisStatusErrorDTO("artemisApp.exerciseChatbot.errors.askUserQuizFailed", null),
                null, null, null, null, null);
        when(irisAskUserService.resetAskUserPipelineAfterPyrisFailure(job)).thenReturn(true);
        when(irisChatSessionService.handleStatusUpdate(job, expectedStatusUpdate, null)).thenReturn(job);
        when(pyrisJobService.removeJob(job)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        var inOrder = inOrder(irisAskUserService, irisChatSessionService, pyrisJobService);
        inOrder.verify(irisAskUserService).resetAskUserPipelineAfterPyrisFailure(job);
        inOrder.verify(irisChatSessionService).handleStatusUpdate(job, expectedStatusUpdate, null);
        inOrder.verify(pyrisJobService).removeJob(job);
    }

    @Test
    void failedRegularChatJobDoesNotResetAskUserPipeline() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO(null, PyrisRunState.FAILED, null, null, null, null, null, null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate, null)).thenReturn(job);
        when(pyrisJobService.removeJob(job)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisAskUserService, never()).resetAskUserPipelineAfterPyrisFailure(job);
    }

    @Test
    void expiredRegularChatJobKeepsOriginalTimeoutBehavior() {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);

        service.handlePyrisJobExpiredEvent(new PyrisJobExpiredEvent(job));

        verifyNoMoreInteractions(irisChatSessionService, pyrisJobService, irisAskUserService);
    }

    @Test
    void expiredAskUserChatJobIsHandledAsQuizFailureStatusUpdate() {
        var job = new ChatJob("ask-user-run", 1L, 2L, 3L, null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        var expectedStatusUpdate = new PyrisChatStatusUpdateDTO(null, PyrisRunState.FAILED, new PyrisStatusErrorDTO("artemisApp.exerciseChatbot.errors.askUserQuizFailed", null),
                null, null, null, null, null);
        when(irisAskUserService.resetAskUserPipelineAfterPyrisFailure(job)).thenReturn(true);
        when(irisChatSessionService.handleStatusUpdate(job, expectedStatusUpdate, null)).thenReturn(job);
        when(pyrisJobService.removeJob(job)).thenReturn(job);

        service.handlePyrisJobExpiredEvent(new PyrisJobExpiredEvent(job));

        var inOrder = inOrder(irisAskUserService, irisChatSessionService, pyrisJobService);
        inOrder.verify(irisAskUserService).resetAskUserPipelineAfterPyrisFailure(job);
        inOrder.verify(irisChatSessionService).handleStatusUpdate(job, expectedStatusUpdate, null);
        inOrder.verify(pyrisJobService).removeJob(job);
    }

    @ParameterizedTest
    @EnumSource(PyrisRunState.class)
    void competencyJobLifecycleUsesRunState(PyrisRunState runState) {
        var job = new CompetencyExtractionJob("competency-run", 1L, 2L);
        var statusUpdate = new PyrisCompetencyStatusUpdateDTO(runState, null, List.of(), List.of());
        when(competencyGenerationService.handleStatusUpdate(job, statusUpdate)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(competencyGenerationService).handleStatusUpdate(job, statusUpdate);
        verifyLifecycle(job, runState);
    }

    @ParameterizedTest
    @EnumSource(PyrisRunState.class)
    void faqIngestionJobLifecycleUsesRunState(PyrisRunState runState) {
        var job = new FaqIngestionWebhookJob("faq-run", 1L, 2L);
        var statusUpdate = new PyrisFaqIngestionStatusUpdateDTO(null, runState, null, 3L);

        service.handleStatusUpdate(job, statusUpdate);

        verifyLifecycle(job, runState);
    }

    @ParameterizedTest
    @EnumSource(PyrisRunState.class)
    void tutorSuggestionJobLifecycleUsesRunState(PyrisRunState runState) {
        var job = new TutorSuggestionJob("tutor-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new TutorSuggestionStatusUpdateDTO(null, null, runState, null, List.of());
        when(irisTutorSuggestionSessionService.handleStatusUpdate(job, statusUpdate)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisTutorSuggestionSessionService).handleStatusUpdate(job, statusUpdate);
        verifyLifecycle(job, runState);
    }

    @ParameterizedTest
    @EnumSource(PyrisRunState.class)
    void autonomousTutorJobLifecycleUsesRunState(PyrisRunState runState) {
        var job = new AutonomousTutorJob("autonomous-run", 1L, 2L);
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO(null, false, null, runState, null, List.of());

        service.handleStatusUpdate(job, statusUpdate);

        verify(autonomousTutorService).handleStatusUpdate(job, statusUpdate);
        verifyLifecycle(job, runState);
    }

    @Test
    void globalSearchThinkingIsDerivedFromRunState() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var runningUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null);

        service.handleStatusUpdate(job, runningUpdate);

        verify(irisWebsocketService).send("student1", "global-search-answer", new IrisGlobalSearchAnswerWebsocketDTO("global-run", true, null, null));
        verify(pyrisJobService).updateJob(job);

        var terminalUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FINISHED, null, "answer", null);

        service.handleStatusUpdate(job, terminalUpdate);

        verify(irisWebsocketService).send("student1", "global-search-answer", new IrisGlobalSearchAnswerWebsocketDTO("global-run", false, "answer", null));
        verify(pyrisJobService).removeJob(job);

        var failedJob = new GlobalSearchAnswerJob("global-failed-run", "student1");
        var failedUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FAILED, null, null, null);

        service.handleStatusUpdate(failedJob, failedUpdate);

        verify(irisWebsocketService).send("student1", "global-search-answer", new IrisGlobalSearchAnswerWebsocketDTO("global-failed-run", false, null, null));
        verify(pyrisJobService).removeJob(failedJob);
    }

    @ParameterizedTest(name = "result={0}, state={1}, errorCode={2}")
    @MethodSource("ingestionBehaviorMatrix")
    void lectureIngestionInteractionsMatchRunStateBehavior(boolean resultPresent, PyrisRunState runState, boolean errorCodeSet) {
        var job = new LectureIngestionWebhookJob("lecture-run", 1L, 2L, 42L);
        var result = resultPresent ? "checkpoint" : null;
        var error = errorCodeSet ? new PyrisStatusErrorDTO("failed", "YOUTUBE_PRIVATE") : null;
        var displayPageNumbers = List.of(1, 2, -1);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(result, runState, error, 7L, displayPageNumbers);

        service.handleStatusUpdate(job, statusUpdate);

        var inOrder = inOrder(processingStateCallbackApi, pyrisJobService);
        if (resultPresent) {
            inOrder.verify(processingStateCallbackApi).handleCheckpointData(42L, "lecture-run", "checkpoint");
        }
        if (runState == PyrisRunState.RUNNING) {
            inOrder.verify(pyrisJobService).updateJob(job);
            inOrder.verify(processingStateCallbackApi).handleHeartbeat(42L, "lecture-run");
        }
        else {
            boolean success = runState == PyrisRunState.FINISHED;
            String expectedErrorCode = !success && errorCodeSet ? "YOUTUBE_PRIVATE" : null;
            List<Integer> expectedDisplayPageNumbers = success ? displayPageNumbers : null;
            inOrder.verify(processingStateCallbackApi).handleIngestionComplete(42L, "lecture-run", success, expectedErrorCode, expectedDisplayPageNumbers);
            inOrder.verify(pyrisJobService).removeJob(job);
        }
        verifyNoMoreInteractions(processingStateCallbackApi, pyrisJobService);
    }

    @Test
    void nullRunStateIsTreatedAsFailedTerminalForEveryStatusUpdateType() {
        var chatJob = new ChatJob("chat-null", 1L, 2L, 3L, null, null, null);
        var chatUpdate = new PyrisChatStatusUpdateDTO("answer", null, null, null, null, null, null, null);
        var normalizedChatUpdate = new PyrisChatStatusUpdateDTO("answer", PyrisRunState.FAILED, null, null, null, null, null, null, null, null, null, null);
        when(irisChatSessionService.handleStatusUpdate(chatJob, normalizedChatUpdate, null)).thenReturn(chatJob);

        service.handleStatusUpdate(chatJob, chatUpdate);

        verify(irisChatSessionService).handleStatusUpdate(chatJob, normalizedChatUpdate, null);
        verify(pyrisJobService).removeJob(chatJob);

        var competencyJob = new CompetencyExtractionJob("competency-null", 1L, 2L);
        var competencyUpdate = new PyrisCompetencyStatusUpdateDTO(null, null, List.of(), List.of());
        var normalizedCompetencyUpdate = new PyrisCompetencyStatusUpdateDTO(PyrisRunState.FAILED, null, List.of(), List.of());
        when(competencyGenerationService.handleStatusUpdate(competencyJob, normalizedCompetencyUpdate)).thenReturn(competencyJob);

        service.handleStatusUpdate(competencyJob, competencyUpdate);

        verify(competencyGenerationService).handleStatusUpdate(competencyJob, normalizedCompetencyUpdate);
        verify(pyrisJobService).removeJob(competencyJob);

        var globalJob = new GlobalSearchAnswerJob("global-null", "student1");

        service.handleStatusUpdate(globalJob, new PyrisGlobalSearchAnswerStatusUpdateDTO(null, null, null, null));

        verify(irisWebsocketService).send("student1", "global-search-answer", new IrisGlobalSearchAnswerWebsocketDTO("global-null", false, null, null));
        verify(pyrisJobService).removeJob(globalJob);

        var lectureJob = new LectureIngestionWebhookJob("lecture-null", 1L, 2L, 42L);

        service.handleStatusUpdate(lectureJob, new PyrisLectureIngestionStatusUpdateDTO(null, null, null, 7L, null));

        verify(processingStateCallbackApi).handleIngestionComplete(42L, "lecture-null", false, null, null);
        verify(pyrisJobService).removeJob(lectureJob);

        var faqJob = new FaqIngestionWebhookJob("faq-null", 1L, 2L);

        service.handleStatusUpdate(faqJob, new PyrisFaqIngestionStatusUpdateDTO(null, null, null, 3L));

        verify(pyrisJobService).removeJob(faqJob);

        var tutorJob = new TutorSuggestionJob("tutor-null", 1L, 2L, 3L, null, null, null);
        var tutorUpdate = new TutorSuggestionStatusUpdateDTO(null, null, null, null, List.of());
        var normalizedTutorUpdate = new TutorSuggestionStatusUpdateDTO(null, null, PyrisRunState.FAILED, null, List.of());
        when(irisTutorSuggestionSessionService.handleStatusUpdate(tutorJob, normalizedTutorUpdate)).thenReturn(tutorJob);

        service.handleStatusUpdate(tutorJob, tutorUpdate);

        verify(irisTutorSuggestionSessionService).handleStatusUpdate(tutorJob, normalizedTutorUpdate);
        verify(pyrisJobService).removeJob(tutorJob);

        var autonomousJob = new AutonomousTutorJob("autonomous-null", 1L, 2L);
        var autonomousUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO(null, false, null, null, null, List.of());
        var normalizedAutonomousUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO(null, false, null, PyrisRunState.FAILED, null, List.of());

        service.handleStatusUpdate(autonomousJob, autonomousUpdate);

        verify(autonomousTutorService).handleStatusUpdate(autonomousJob, normalizedAutonomousUpdate);
        verify(pyrisJobService).removeJob(autonomousJob);

        // The null-runState warning itself is not asserted here: log-appender capture is racy
        // under the parallel test runner (a concurrently booting Spring context reinitializes
        // logback mid-test). The normalization behavior above is the load-bearing contract.
    }

    private static Stream<Arguments> ingestionBehaviorMatrix() {
        return Stream.of(false, true).flatMap(resultPresent -> Stream.of(PyrisRunState.values())
                .flatMap(runState -> Stream.of(false, true).map(errorCodeSet -> Arguments.of(resultPresent, runState, errorCodeSet))));
    }

    private void verifyLifecycle(PyrisJob job, PyrisRunState runState) {
        if (runState.isTerminal()) {
            verify(pyrisJobService).removeJob(job);
            verify(pyrisJobService, never()).updateJob(job);
        }
        else {
            verify(pyrisJobService).updateJob(job);
            verify(pyrisJobService, never()).removeJob(job);
        }
    }
}
