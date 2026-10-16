package de.tum.cit.aet.artemis.iris.web.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.iris.exception.IrisVideoSourceUnavailableException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisPreparedLectureIngestionJobDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWorkerClaimResponseDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;

/**
 * Unit tests for {@link PyrisInternalIngestionWorkerResource#claimJobs}, focused on isolating one
 * claimed item's preparation failure from the rest of a mixed batch.
 */
class PyrisInternalIngestionWorkerResourceTest {

    private static final String SECRET = "test-secret";

    private PyrisWebhookService pyrisWebhookService;

    private ProcessingStateCallbackApi processingStateCallbackApi;

    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    private PyrisInternalIngestionWorkerResource resource;

    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        pyrisWebhookService = mock(PyrisWebhookService.class);
        processingStateCallbackApi = mock(ProcessingStateCallbackApi.class);
        lectureUnitRepositoryApi = mock(LectureUnitRepositoryApi.class);

        resource = new PyrisInternalIngestionWorkerResource(pyrisWebhookService, Optional.of(processingStateCallbackApi), Optional.of(lectureUnitRepositoryApi));
        ReflectionTestUtils.setField(resource, "pyrisSecretToken", SECRET);

        servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(SECRET);
    }

    private static ClaimedIngestionUnitDTO claim(long lectureUnitId, String claimToken) {
        return new ClaimedIngestionUnitDTO(lectureUnitId, "v1:fp", "v1:fp-without-video", false, ProcessingPhase.INGESTING, claimToken);
    }

    @Test
    void shouldContinueTheBatchWhenOnePreparationThrows() {
        String claimedAtFailing = "claim-failing";
        String claimedAtOk = "claim-ok";
        ClaimedIngestionUnitDTO failingClaim = claim(1L, claimedAtFailing);
        ClaimedIngestionUnitDTO okClaim = claim(2L, claimedAtOk);
        when(processingStateCallbackApi.claimUnitsForWorker(anyString(), anyInt())).thenReturn(List.of(failingClaim, okClaim));

        AttachmentVideoUnit failingUnit = new AttachmentVideoUnit();
        failingUnit.setId(1L);
        AttachmentVideoUnit okUnit = new AttachmentVideoUnit();
        okUnit.setId(2L);
        when(lectureUnitRepositoryApi.findByIdElseThrow(1L)).thenReturn(failingUnit);
        when(lectureUnitRepositoryApi.findByIdElseThrow(2L)).thenReturn(okUnit);

        when(pyrisWebhookService.prepareLectureUnitIngestion(eq(failingUnit), anyString(), anyString(), eq(false)))
                .thenThrow(new IllegalStateException("attachment file is unreadable"));
        PyrisPreparedLectureIngestionJobDTO okPrepared = preparedJob(2L, "job-token-2", "v1:fp", "https://www.youtube.com/watch?v=x");
        PyrisWebhookLectureIngestionExecutionDTO okExecutionDto = okPrepared.executionDTO();
        when(pyrisWebhookService.prepareLectureUnitIngestion(eq(okUnit), anyString(), anyString(), eq(false))).thenReturn(okPrepared);
        when(processingStateCallbackApi.activateClaimedJob(eq(2L), eq("job-token-2"), eq(ProcessingPhase.INGESTING), anyString(), anyString(), eq(claimedAtOk))).thenReturn(true);

        ResponseEntity<PyrisWorkerClaimResponseDTO> response = resource.claimJobs(new PyrisWorkerClaimRequestDTO("worker-1", 2), servletRequest);

        // The failing item is skipped without aborting the batch; the healthy item is still delivered.
        assertThat(response.getBody().jobs()).containsExactly(okExecutionDto);
        // A preparation exception must never be treated as "not processable": that would permanently
        // mark the unit SKIPPED for what may be a transient failure.
        verify(processingStateCallbackApi, never()).markClaimedUnitSkipped(eq(1L), any());
        // Preparation throws before prepareLectureAdditionJob registers a token, so there is nothing
        // to revoke for the failing item.
        verify(pyrisWebhookService, never()).revokePreparedIngestionJob(any());
        // The failure is charged to the failing item's claim, so an error that repeats runs out of retries instead of looping for free.
        verify(processingStateCallbackApi).failClaimedUnitPreparation(1L, claimedAtFailing);
        verify(processingStateCallbackApi, never()).failClaimedUnitPreparation(eq(2L), any());
    }

    private static PyrisPreparedLectureIngestionJobDTO preparedJob(long lectureUnitId, String jobToken, String fingerprint, String videoLink) {
        PyrisLectureUnitWebhookDTO unitDto = new PyrisLectureUnitWebhookDTO("", 0, null, lectureUnitId, "", 1L, "", 1L, "", "", "", videoLink, null, fingerprint);
        return new PyrisPreparedLectureIngestionJobDTO(jobToken, new PyrisWebhookLectureIngestionExecutionDTO(unitDto, lectureUnitId, null));
    }

    /**
     * A unit whose only content is a video that cannot be loaded right now is charged its own retryable failure, which the outage pause ignores, instead
     * of the generic preparation failure.
     */
    @Test
    void shouldChargeAVideoThatCannotBeLoadedWithItsOwnFailure() {
        when(processingStateCallbackApi.claimUnitsForWorker(anyString(), anyInt())).thenReturn(List.of(claim(1L, "claim-1")));
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(1L);
        when(lectureUnitRepositoryApi.findByIdElseThrow(1L)).thenReturn(unit);
        when(pyrisWebhookService.prepareLectureUnitIngestion(eq(unit), anyString(), anyString(), eq(false))).thenThrow(new IrisVideoSourceUnavailableException(1L));

        ResponseEntity<PyrisWorkerClaimResponseDTO> response = resource.claimJobs(new PyrisWorkerClaimRequestDTO("worker-1", 1), servletRequest);

        assertThat(response.getBody().jobs()).isEmpty();
        verify(processingStateCallbackApi).failClaimedUnitVideoUnavailable(1L, "claim-1");
        verify(processingStateCallbackApi, never()).failClaimedUnitPreparation(anyLong(), any());
    }

    /**
     * The claim activates with what the payload carries: the fingerprint Iris stamps, so the confirmed fingerprint equals the index stamp, and no
     * transcription phase for a run sent without its video.
     */
    @Test
    void shouldActivateWithTheFingerprintAndPhaseOfThePayloadSent() {
        ClaimedIngestionUnitDTO transcribingClaim = new ClaimedIngestionUnitDTO(1L, "v1:full", "v1:without-video", false, ProcessingPhase.TRANSCRIBING, "claim-1");
        when(processingStateCallbackApi.claimUnitsForWorker(anyString(), anyInt())).thenReturn(List.of(transcribingClaim));
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(1L);
        when(lectureUnitRepositoryApi.findByIdElseThrow(1L)).thenReturn(unit);
        PyrisPreparedLectureIngestionJobDTO prepared = preparedJob(1L, "job-token-1", "v1:without-video", null);
        when(pyrisWebhookService.prepareLectureUnitIngestion(unit, "v1:full", "v1:without-video", false)).thenReturn(prepared);
        when(processingStateCallbackApi.activateClaimedJob(anyLong(), anyString(), any(), anyString(), anyString(), anyString())).thenReturn(true);

        resource.claimJobs(new PyrisWorkerClaimRequestDTO("worker-1", 1), servletRequest);

        verify(processingStateCallbackApi).activateClaimedJob(1L, "job-token-1", ProcessingPhase.INGESTING, "v1:without-video", "worker-1", "claim-1");
    }
}
