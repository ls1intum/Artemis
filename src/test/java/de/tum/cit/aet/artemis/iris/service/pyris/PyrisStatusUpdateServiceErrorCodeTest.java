package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
import de.tum.cit.aet.artemis.iris.service.session.IrisTutorSuggestionSessionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

/**
 * Unit test verifying that the {@code error.code} from a terminal
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
                mock(IrisTutorSuggestionSessionService.class), mock(AutonomousTutorService.class), Optional.of(callbackApi), mock(IrisWebsocketService.class),
                mock(IrisStruggleInterventionService.class), mock(IrisStruggleTriggerService.class));
    }

    @Test
    void errorCodeIsForwardedOnTerminalErrorCallback() {
        // jobId, courseId, lectureId, lectureUnitId
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(null, PyrisRunState.FAILED, new PyrisStatusErrorDTO("video is private", "YOUTUBE_PRIVATE"), 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        // The callback API must receive the errorCode "YOUTUBE_PRIVATE" (not null)
        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(false), eq("YOUTUBE_PRIVATE"), eq(null));
    }

    @Test
    void nullErrorCodeIsForwardedOnTerminalSuccessCallback() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(null, PyrisRunState.FINISHED, null, 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(null));
    }

    @Test
    void displayPageNumbersAreReadOnlyFromDedicatedField() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("{\"displayPageNumbers\":[9,9,9]}", PyrisRunState.FINISHED, null, 7L, List.of(1, 2, -1));

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(List.of(1, 2, -1)));
        verify(callbackApi).handleCheckpointData(eq(42L), eq("job-token-abc"), eq("{\"displayPageNumbers\":[9,9,9]}"));
    }

    @Test
    void missingDisplayPageNumbersRemainNullableOnSuccess() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("done", PyrisRunState.FINISHED, null, 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(null));
    }
}
