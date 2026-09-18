package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
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

    private FeatureToggleService featureToggleService;

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
        doReturn(mock(DistributedLock.class)).when(distributedDataProvider).getLock(anyString());

        Optional<IrisLectureApi> irisLectureApi = Optional.of(mock(IrisLectureApi.class));
        featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);

        callbackService = new ProcessingStateCallbackService(processingStateRepository, transcriptionRepository, attachmentRepository, irisLectureApi, websocketMessagingService,
                contentFingerprintService, distributedDataProvider, featureToggleService, 2, 20, Duration.ofSeconds(90), 8);

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
        when(processingStateRepository.findIdleForDispatch(any(), eq(2))).thenReturn(List.of(testState));
        when(processingStateRepository.claimIdleForDispatch(eq(500L), any())).thenReturn(1);

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
        when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of());

        callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 99);

        verify(processingStateRepository).findIdleForDispatch(any(), eq(8));
    }

    @Test
    void claimWithoutFreeSlotsClaimsNothingButStillMarksTheWorkerSeen() {
        List<ClaimedIngestionUnitDTO> claims = callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 0);

        assertThat(claims).isEmpty();
        verify(processingStateRepository, never()).findIdleForDispatch(any(), anyInt());
        verify(processingStateRepository, never()).findStatesReadyForRetry(any(), any(), anyInt());
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
    void claimUnitsForWorkerHandsOutNothingWhileTheFeatureIsDisabled() {
        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(false);

        assertThat(callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 2)).isEmpty();

        verify(processingStateRepository, never()).findIdleForDispatch(any(), anyInt());
        assertThat(workerMapBacking).containsKey("lastSeenAt");
    }

    @Test
    void activateClaimedJobActivatesTheClaimAtomically() {
        ZonedDateTime claimedAt = ZonedDateTime.now();
        when(processingStateRepository.activateClaimedJob(eq(100L), eq(ProcessingPhase.INGESTING), eq("token-abc"), eq("v1:test-fingerprint"), eq(WORKER_BOOT_ID), eq(claimedAt),
                any())).thenReturn(1);
        when(processingStateRepository.findByLectureUnit_Id(100L)).thenReturn(Optional.of(testState));

        assertThat(callbackService.activateClaimedJob(100L, "token-abc", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, claimedAt)).isTrue();

        verify(processingStateRepository).activateClaimedJob(eq(100L), eq(ProcessingPhase.INGESTING), eq("token-abc"), eq("v1:test-fingerprint"), eq(WORKER_BOOT_ID), eq(claimedAt),
                any());
        verify(processingStateRepository, never()).save(any());
    }

    @Test
    void activateClaimedJobIgnoresAnActivationWhoseClaimLapsed() {
        when(processingStateRepository.activateClaimedJob(anyLong(), any(), anyString(), anyString(), anyString(), any(), any())).thenReturn(0);

        assertThat(callbackService.activateClaimedJob(100L, "token-late", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, ZonedDateTime.now())).isFalse();

        verify(processingStateRepository, never()).save(any());
        verify(processingStateRepository, never()).findByLectureUnit_Id(anyLong());
    }

    @Test
    void activateClaimedJobIgnoresALateActivationForAClaimThatHasBeenSupersededByANewerOne() {
        // Worker A's claim of this unit lapsed (its request stalled without crashing); the unit was
        // re-claimed and is now sitting IDLE with a NEW claim marker, not yet activated. Worker A's
        // stale activation for its OLD claim arrives late. The atomic guard matches on the exact claim
        // marker, so the stale activation cannot be conflated with the newer, still-unactivated claim:
        // it matches nothing instead of activating that newer claim with worker A's stale job token.
        ZonedDateTime staleClaimedAt = ZonedDateTime.now().minusMinutes(25);
        when(processingStateRepository.activateClaimedJob(eq(100L), any(), anyString(), anyString(), anyString(), eq(staleClaimedAt), any())).thenReturn(0);

        assertThat(callbackService.activateClaimedJob(100L, "token-from-lapsed-claim", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, staleClaimedAt)).isFalse();

        verify(processingStateRepository, never()).save(any());
        verify(processingStateRepository, never()).findByLectureUnit_Id(anyLong());
    }

    @Test
    void renewWorkerLeasesRenewsKnownRunsAndReportsUnknownTokensRevoked() {
        testState.setPhase(ProcessingPhase.INGESTING);
        testState.setIngestionJobToken("token-known");
        when(processingStateRepository.findByIngestionJobToken("token-known")).thenReturn(Optional.of(testState));
        when(processingStateRepository.findByIngestionJobToken("token-unknown")).thenReturn(Optional.empty());
        when(processingStateRepository.renewLease(eq(500L), eq("token-known"), any(), eq(WORKER_BOOT_ID))).thenReturn(1);

        List<String> revoked = callbackService.renewWorkerLeases(WORKER_BOOT_ID, List.of("token-known", "token-unknown"));

        assertThat(revoked).containsExactly("token-unknown");
        assertThat(testState.getLastHeartbeatAt()).isNotNull();
        assertThat(testState.getLockedBy()).isEqualTo(WORKER_BOOT_ID);
        verify(processingStateRepository).renewLease(eq(500L), eq("token-known"), any(), eq(WORKER_BOOT_ID));
        verify(processingStateRepository, never()).save(testState);
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
    void renewWorkerLeasesReportsTheTokenRevokedWhenATerminalCallbackWonTheRace() {
        // The read here still sees the run as in-flight, but the atomic update reports 0 rows
        // affected -- a terminal callback cleared the token in between. This is finding 5: without
        // the atomic guard, a plain save() would have overwritten the just-written DONE/FAILED state
        // back to INGESTING with the stale token and lease.
        testState.setPhase(ProcessingPhase.INGESTING);
        testState.setIngestionJobToken("token-racing");
        when(processingStateRepository.findByIngestionJobToken("token-racing")).thenReturn(Optional.of(testState));
        when(processingStateRepository.renewLease(eq(500L), eq("token-racing"), any(), eq(WORKER_BOOT_ID))).thenReturn(0);

        List<String> revoked = callbackService.renewWorkerLeases(WORKER_BOOT_ID, List.of("token-racing"));

        assertThat(revoked).containsExactly("token-racing");
        assertThat(testState.getLastHeartbeatAt()).isNull();
        verify(processingStateRepository, never()).save(testState);
    }

    @Test
    void markClaimedUnitSkippedMarksSkippedWhenTheClaimIsStillCurrent() {
        ZonedDateTime claimedAt = ZonedDateTime.now();
        when(processingStateRepository.markSkippedIfStillClaimed(eq(100L), eq(claimedAt), any())).thenReturn(1);

        assertThat(callbackService.markClaimedUnitSkipped(100L, claimedAt)).isTrue();

        verify(processingStateRepository).markSkippedIfStillClaimed(eq(100L), eq(claimedAt), any());
    }

    @Test
    void markClaimedUnitSkippedIgnoresAResultWhoseClaimIsNoLongerCurrent() {
        // Worker A's original claim of this unit lapsed; the unit was re-claimed and already
        // activated into a live run before worker A's stale "not applicable" result for its OLD,
        // now-defunct claim finally arrives. The atomic guard matches on the exact claim marker
        // (unlike a phase-only check, which two different claims could pass through one after
        // another), so the stale result cannot be conflated with the newer claim: it matches nothing
        // instead of cancelling the newer claim's run. This is finding 3: without the claim-marker
        // match, the previous unconditional save would have overwritten an active run with SKIPPED.
        ZonedDateTime staleClaimedAt = ZonedDateTime.now().minusMinutes(25);
        when(processingStateRepository.markSkippedIfStillClaimed(eq(100L), eq(staleClaimedAt), any())).thenReturn(0);

        assertThat(callbackService.markClaimedUnitSkipped(100L, staleClaimedAt)).isFalse();
    }
}
