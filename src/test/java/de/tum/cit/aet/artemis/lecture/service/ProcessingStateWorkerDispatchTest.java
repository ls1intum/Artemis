package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Unit tests for the pull-based worker dispatch in {@link ProcessingStateCallbackService}: claiming,
 * claim activation, lease renewal, and the suppression of legacy push dispatch while a worker is
 * present. The worker-seen state is backed by a real map so the mode switch is tested end to end.
 */
class ProcessingStateWorkerDispatchTest {

    private static final String WORKER_BOOT_ID = "worker-boot-1";

    private ProcessingStateCallbackService callbackService;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private LectureTranscriptionRepository transcriptionRepository;

    private Map<String, String> workerMapBacking;

    private AttachmentVideoUnit testUnit;

    private LectureUnitProcessingState testState;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        transcriptionRepository = mock(LectureTranscriptionRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        WebsocketMessagingService websocketMessagingService = mock(WebsocketMessagingService.class);
        LectureUnitContentFingerprintService contentFingerprintService = mock(LectureUnitContentFingerprintService.class);
        when(contentFingerprintService.computeFingerprint(any())).thenReturn("v1:test-fingerprint");

        workerMapBacking = new HashMap<>();
        DistributedMap<String, String> workerMap = mock(DistributedMap.class);
        when(workerMap.get(anyString())).thenAnswer(invocation -> workerMapBacking.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> workerMapBacking.put(invocation.getArgument(0), invocation.getArgument(1))).when(workerMap).put(anyString(), anyString());
        DistributedDataProvider distributedDataProvider = mock(DistributedDataProvider.class);
        doReturn(workerMap).when(distributedDataProvider).getMap(anyString());

        ObjectProvider<IrisLectureApi> irisLectureApi = mock(ObjectProvider.class);
        when(irisLectureApi.getIfAvailable()).thenReturn(mock(IrisLectureApi.class));

        callbackService = new ProcessingStateCallbackService(processingStateRepository, transcriptionRepository, attachmentRepository, irisLectureApi, websocketMessagingService,
                contentFingerprintService, distributedDataProvider, 2);

        Lecture lecture = new Lecture();
        lecture.setId(1L);
        testUnit = new AttachmentVideoUnit();
        testUnit.setId(100L);
        testUnit.setLecture(lecture);
        testState = new LectureUnitProcessingState(testUnit);
        testState.setId(500L);

        when(transcriptionRepository.findByLectureUnit_Id(100L)).thenReturn(Optional.empty());
    }

    @Test
    void claimReturnsPreparedScalarsAndMarksWorkerSeen() {
        when(processingStateRepository.claimJobsForDispatch(any(), eq(2))).thenReturn(List.of(testState));

        List<ClaimedIngestionUnitDTO> claims = callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 2);

        assertThat(claims).hasSize(1);
        ClaimedIngestionUnitDTO claim = claims.getFirst();
        assertThat(claim.lectureUnitId()).isEqualTo(100L);
        assertThat(claim.contentFingerprint()).isEqualTo("v1:test-fingerprint");
        assertThat(claim.targetPhase()).isEqualTo(ProcessingPhase.INGESTING);
        assertThat(workerMapBacking).containsKey("lastSeenAt");
    }

    @Test
    void claimClampsRequestedJobsToTheSanityBound() {
        when(processingStateRepository.claimJobsForDispatch(any(), anyInt())).thenReturn(List.of());

        callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 99);

        verify(processingStateRepository).claimJobsForDispatch(any(), eq(8));
    }

    @Test
    void claimWithoutFreeSlotsClaimsNothingButStillMarksTheWorkerSeen() {
        List<ClaimedIngestionUnitDTO> claims = callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 0);

        assertThat(claims).isEmpty();
        verify(processingStateRepository, never()).claimJobsForDispatch(any(), anyInt());
        assertThat(workerMapBacking).containsKey("lastSeenAt");
    }

    @Test
    void pushDispatchIsSuppressedWhileAWorkerIsPresent() {
        workerMapBacking.put("lastSeenAt", Instant.now().toString());

        callbackService.dispatchPendingJobs();

        // Returns before the capacity check: jobs stay IDLE for the worker's next claim.
        verify(processingStateRepository, never()).countByPhaseIn(any());
    }

    @Test
    void pushDispatchResumesAfterTheWorkerDisappears() {
        workerMapBacking.put("lastSeenAt", Instant.now().minusSeconds(600).toString());
        when(processingStateRepository.countByPhaseIn(any())).thenReturn(2L);

        callbackService.dispatchPendingJobs();

        // Past the worker grace, the legacy push path runs again (here it stops at full capacity).
        verify(processingStateRepository).countByPhaseIn(any());
    }

    @Test
    void activateClaimedJobOpensTheWorkerLease() {
        when(processingStateRepository.findByLectureUnit_Id(100L)).thenReturn(Optional.of(testState));

        callbackService.activateClaimedJob(100L, "token-abc", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID);

        assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.INGESTING);
        assertThat(testState.getIngestionJobToken()).isEqualTo("token-abc");
        assertThat(testState.getContentFingerprint()).isEqualTo("v1:test-fingerprint");
        assertThat(testState.getLastHeartbeatAt()).isNotNull();
        assertThat(testState.getLockedBy()).isEqualTo(WORKER_BOOT_ID);
        verify(processingStateRepository).save(testState);
    }

    @Test
    void renewWorkerLeasesRenewsKnownRunsAndReportsUnknownTokensRevoked() {
        testState.setPhase(ProcessingPhase.INGESTING);
        testState.setIngestionJobToken("token-known");
        when(processingStateRepository.findByIngestionJobToken("token-known")).thenReturn(Optional.of(testState));
        when(processingStateRepository.findByIngestionJobToken("token-unknown")).thenReturn(Optional.empty());

        List<String> revoked = callbackService.renewWorkerLeases(WORKER_BOOT_ID, List.of("token-known", "token-unknown"));

        assertThat(revoked).containsExactly("token-unknown");
        assertThat(testState.getLastHeartbeatAt()).isNotNull();
        assertThat(testState.getLockedBy()).isEqualTo(WORKER_BOOT_ID);
        verify(processingStateRepository).save(testState);
    }

    @Test
    void renewWorkerLeasesReportsTokensOfFinishedRunsRevoked() {
        testState.setPhase(ProcessingPhase.DONE);
        testState.setIngestionJobToken("token-done");
        when(processingStateRepository.findByIngestionJobToken("token-done")).thenReturn(Optional.of(testState));

        List<String> revoked = callbackService.renewWorkerLeases(WORKER_BOOT_ID, List.of("token-done"));

        assertThat(revoked).containsExactly("token-done");
        assertThat(testState.getLastHeartbeatAt()).isNull();
    }

    @Test
    void markClaimedUnitSkippedTransitionsToSkipped() {
        when(processingStateRepository.findByLectureUnit_Id(100L)).thenReturn(Optional.of(testState));

        callbackService.markClaimedUnitSkipped(100L);

        assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.SKIPPED);
        verify(processingStateRepository).save(testState);
    }
}
