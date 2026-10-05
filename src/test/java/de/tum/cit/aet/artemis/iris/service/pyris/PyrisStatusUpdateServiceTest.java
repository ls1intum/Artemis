package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.userTopic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisEntitySourceDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

class PyrisStatusUpdateServiceTest {

    private PyrisJobService pyrisJobService;

    private IrisChatSessionService irisChatSessionService;

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
        competencyGenerationService = mock(IrisCompetencyGenerationService.class);
        irisTutorSuggestionSessionService = mock(IrisTutorSuggestionSessionService.class);
        autonomousTutorService = mock(AutonomousTutorService.class);
        processingStateCallbackApi = mock(ProcessingStateCallbackApi.class);
        irisWebsocketService = mock(IrisWebsocketService.class);
        service = new PyrisStatusUpdateService(pyrisJobService, irisChatSessionService, competencyGenerationService, irisTutorSuggestionSessionService, autonomousTutorService,
                Optional.of(processingStateCallbackApi), irisWebsocketService, mock(IrisStruggleInterventionService.class), mock(IrisStruggleTriggerService.class));
    }

    @ParameterizedTest
    @EnumSource(PyrisRunState.class)
    void chatJobLifecycleUsesRunState(PyrisRunState runState) {
        var job = new ChatJob("chat-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO(null, runState, null, null, null, null, null, null);
        when(irisChatSessionService.handleStatusUpdate(job, statusUpdate)).thenReturn(job);

        service.handleStatusUpdate(job, statusUpdate);

        verify(irisChatSessionService).handleStatusUpdate(job, statusUpdate);
        verifyLifecycle(job, runState);
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

    @Test
    void tutorSuggestionJobIsRemovedWhenSessionNoLongerExists() {
        var job = new TutorSuggestionJob("tutor-run", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new TutorSuggestionStatusUpdateDTO(null, null, PyrisRunState.RUNNING, null, List.of());
        when(irisTutorSuggestionSessionService.handleStatusUpdate(job, statusUpdate)).thenReturn(null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(pyrisJobService).removeJob(job);
        verify(pyrisJobService, never()).updateJob(any());
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
    void globalSearchEntitySourcesAreForwardedOnTheTerminalUpdate() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var entitySource = new PyrisEntitySourceDTO("exercise", 42L, new PyrisLectureSearchResultDTO.CourseDTO(9L, "Patterns"), "W03E03 Flyweight Pattern",
                "Programming exercise: 'W03E03 Flyweight Pattern'", "/courses/9/exercises/42", "programming");
        var terminalUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FINISHED, null, "answer.[1]", null, null, null, List.of(entitySource));

        service.handleStatusUpdate(job, terminalUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-run", false, "answer.[1]", null, null, null, List.of(entitySource), false, false, null, null, null)));
        verify(pyrisJobService).removeJob(job);
    }

    @Test
    void globalSearchCitationSourceTypesAreForwardedOnTheTerminalUpdate() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var entitySource = new PyrisEntitySourceDTO("exercise", 42L, new PyrisLectureSearchResultDTO.CourseDTO(9L, "Patterns"), "W03E03 Flyweight Pattern",
                "Programming exercise: 'W03E03 Flyweight Pattern'", "/courses/9/exercises/42", "programming");
        var terminalUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FINISHED, null, "About the course.[1] About the slide.[2]", null, null, null,
                List.of(entitySource), null, null, List.of("entity", "lecture"));

        service.handleStatusUpdate(job, terminalUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"), eq(new IrisGlobalSearchAnswerWebsocketDTO("global-run", false,
                "About the course.[1] About the slide.[2]", null, null, null, List.of(entitySource), false, false, null, null, List.of("entity", "lecture"))));
        verify(pyrisJobService).removeJob(job);
    }

    @Test
    void globalSearchPartialResultIsForwardedAsStreamedDraft() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var partialUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null, "Signals are reactive.[1]", 3);

        service.handleStatusUpdate(job, partialUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-run", true, null, null, "Signals are reactive.[1]", 3, false)));
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void globalSearchEmptyPartialResultIsForwardedAsClearDraftInsteadOfEmptyText() {
        // The provider's retry-clear signal (see PartialResultSender on the Pyris side) is an empty
        // partialResult; it must be translated to clearDraft=true with partialResult omitted, since an
        // empty string would not survive IrisGlobalSearchAnswerWebsocketDTO's NON_EMPTY serialization
        // and would reach the client indistinguishable from "no partial result in this message".
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var clearUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null, "", 4);

        service.handleStatusUpdate(job, clearUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-run", true, null, null, null, 4, true)));
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void globalSearchStageIsForwardedOnAThinkingUpdateWithNoPartialResultYet() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var stagedUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null, null, null, null, "searching", null);

        service.handleStatusUpdate(job, stagedUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(IrisGlobalSearchAnswerWebsocketDTO.thinking("global-run", "searching", null)));
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void globalSearchStageSourcesAreForwardedAlongsideTheFoundStage() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var stagedUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null, null, null, null, "found",
                List.of("Advanced Algorithms", "Software Engineering"));

        service.handleStatusUpdate(job, stagedUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(IrisGlobalSearchAnswerWebsocketDTO.thinking("global-run", "found", List.of("Advanced Algorithms", "Software Engineering"))));
        verify(pyrisJobService).updateJob(job);
    }

    @Test
    void globalSearchThinkingIsDerivedFromRunState() {
        var job = new GlobalSearchAnswerJob("global-run", "student1");
        var runningUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null);

        service.handleStatusUpdate(job, runningUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-run", true, null, null)));
        verify(pyrisJobService).updateJob(job);

        var terminalUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FINISHED, null, "answer", null);

        service.handleStatusUpdate(job, terminalUpdate);

        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-run", false, "answer", null)));
        verify(pyrisJobService).removeJob(job);

        var failedJob = new GlobalSearchAnswerJob("global-failed-run", "student1");
        var failedUpdate = new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FAILED, null, null, null);

        service.handleStatusUpdate(failedJob, failedUpdate);

        // A genuine failure must be distinguishable from a successful no-answer result (both otherwise
        // produce the identical isThinking=false, answer=null shape), so the client can offer a retry.
        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-failed-run", false, null, null, null, null, null, false, true, null, null, null)));
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
        when(irisChatSessionService.handleStatusUpdate(chatJob, normalizedChatUpdate)).thenReturn(chatJob);

        service.handleStatusUpdate(chatJob, chatUpdate);

        verify(irisChatSessionService).handleStatusUpdate(chatJob, normalizedChatUpdate);
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

        // A null run state resolves to FAILED (see the test name), which must reach the client as
        // failed=true, not the same shape as a considered no-answer result.
        verify(irisWebsocketService).send(eq("student1"), userTopic("/topic/iris/global-search-answer"),
                eq(new IrisGlobalSearchAnswerWebsocketDTO("global-null", false, null, null, null, null, null, false, true, null, null, null)));
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
