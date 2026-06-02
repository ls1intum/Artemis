package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

/**
 * Unit test verifying that the {@code errorCode} from a terminal
 * {@link PyrisLectureIngestionStatusUpdateDTO} is forwarded to
 * {@link ProcessingStateCallbackApi#handleIngestionComplete}.
 */
class PyrisStatusUpdateServiceErrorCodeTest {

    private ProcessingStateCallbackApi callbackApi;

    private PyrisStatusUpdateService service;

    @BeforeEach
    void setUp() {
        callbackApi = mock(ProcessingStateCallbackApi.class);

        service = new PyrisStatusUpdateService(mock(PyrisJobService.class), mock(IrisChatSessionService.class), mock(IrisCompetencyGenerationService.class),
                mock(IrisTutorSuggestionSessionService.class), mock(AutonomousTutorService.class), Optional.of(callbackApi), mock(IrisWebsocketService.class));
    }

    @Test
    void errorCodeIsForwardedOnTerminalErrorCallback() {
        // jobId, courseId, lectureId, lectureUnitId
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        // Build a terminal stage list with an ERROR stage carrying errorCode "YOUTUBE_PRIVATE"
        // name, weight, state, message, internal, chatMessage
        var errorStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.ERROR, "video is private", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(errorStage), 7L, "YOUTUBE_PRIVATE", null);

        service.handleStatusUpdate(job, statusUpdate);

        // The callback API must receive the errorCode "YOUTUBE_PRIVATE" (not null)
        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(false), eq("YOUTUBE_PRIVATE"), eq(null));
    }

    @Test
    void nullErrorCodeIsForwardedOnTerminalSuccessCallback() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(doneStage), 7L, null, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(null));
    }

    @Test
    void terminalResultIsStillForwardedAsCheckpointData() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("{\"segments\":[{\"start\":0.0,\"end\":1.0,\"text\":\"hi\",\"slideNumber\":0}],\"language\":\"en\"}",
                List.of(doneStage), 7L, null, List.of(1, 2, -1));

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(List.of(1, 2, -1)));
        verify(callbackApi).handleCheckpointData(eq(42L), eq("job-token-abc"),
                eq("{\"segments\":[{\"start\":0.0,\"end\":1.0,\"text\":\"hi\",\"slideNumber\":0}],\"language\":\"en\"}"));
        verify(callbackApi, never()).handleHeartbeat(eq(42L), eq("job-token-abc"));
    }

    @Test
    void slidePageNumbersAreReadOnlyFromDedicatedField() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("{\"slidePageNumbers\":[9,9,9]}", List.of(doneStage), 7L, null, List.of(1, 2, -1));

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(List.of(1, 2, -1)));
    }

    @Test
    void missingSlidePageNumbersRemainNullableOnSuccess() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("done", List.of(doneStage), 7L, null, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(null));
    }

    @Test
    void emptySlidePageNumbersAreForwardedOnSuccessfulTerminalCallback() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("done", List.of(doneStage), 7L, null, List.of());

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(List.of()));
    }

    @Test
    void nonTerminalCallbacksStillDoNotCompleteTheJob() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var inProgressStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.IN_PROGRESS, "running", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("checkpoint", List.of(inProgressStage), 7L, null, List.of(1, 2, -1));

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleCheckpointData(eq(42L), eq("job-token-abc"), eq("checkpoint"));
        verify(callbackApi).handleHeartbeat(eq(42L), eq("job-token-abc"));
        verify(callbackApi, never()).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(List.of(1, 2, -1)));
    }
}
