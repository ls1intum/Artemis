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
                mock(IrisTutorSuggestionSessionService.class), mock(AutonomousTutorService.class), Optional.of(callbackApi));
    }

    @Test
    void errorCodeIsForwardedOnTerminalErrorCallback() {
        // jobId, courseId, lectureId, lectureUnitId
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        // Build a terminal stage list with an ERROR stage carrying errorCode "YOUTUBE_PRIVATE"
        // name, weight, state, message, internal, chatMessage
        var errorStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.ERROR, "video is private", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(errorStage), 7L, "YOUTUBE_PRIVATE");

        service.handleStatusUpdate(job, statusUpdate);

        // The callback API must receive the errorCode "YOUTUBE_PRIVATE" (not null)
        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(false), eq("YOUTUBE_PRIVATE"), eq(null));
    }

    @Test
    void nullErrorCodeIsForwardedOnTerminalSuccessCallback() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(doneStage), 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(null));
    }

    @Test
    void slidePageNumbersAreExtractedFromFinalResultJson() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("{\"slidePageNumbers\":[1,2,-1]}", List.of(doneStage), 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(List.of(1, 2, -1)));
        verify(callbackApi, never()).handleCheckpointData(eq(42L), eq("job-token-abc"), eq("{\"slidePageNumbers\":[1,2,-1]}"));
    }

    @Test
    void slidePageNumbersAreForwardedWithoutFilteringOrReindexing() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("{\"slidePageNumbers\":[1,null,0,-2,-1,10]}", List.of(doneStage), 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(java.util.Arrays.asList(1, null, 0, -2, -1, 10)));
    }

    @Test
    void emptySlidePageNumbersAreForwardedOnSuccessfulTerminalCallback() {
        var job = new LectureIngestionWebhookJob("job-token-abc", 1L, 2L, 42L);

        var doneStage = new PyrisStageDTO("Ingestion", 1, PyrisStageState.DONE, "success", false, null);
        var statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("{\"slidePageNumbers\":[]}", List.of(doneStage), 7L, null);

        service.handleStatusUpdate(job, statusUpdate);

        verify(callbackApi).handleIngestionComplete(eq(42L), eq("job-token-abc"), eq(true), eq(null), eq(null));
    }
}
