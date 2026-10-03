package de.tum.cit.aet.artemis.iris.web.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
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
        return new ClaimedIngestionUnitDTO(lectureUnitId, "v1:fp", false, ProcessingPhase.INGESTING, claimToken);
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

        when(pyrisWebhookService.prepareLectureUnitIngestion(eq(failingUnit), anyString(), eq(false))).thenThrow(new IllegalStateException("attachment file is unreadable"));
        PyrisWebhookLectureIngestionExecutionDTO okExecutionDto = mock(PyrisWebhookLectureIngestionExecutionDTO.class);
        PyrisPreparedLectureIngestionJobDTO okPrepared = new PyrisPreparedLectureIngestionJobDTO("job-token-2", okExecutionDto);
        when(pyrisWebhookService.prepareLectureUnitIngestion(eq(okUnit), anyString(), eq(false))).thenReturn(okPrepared);
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
    }
}
