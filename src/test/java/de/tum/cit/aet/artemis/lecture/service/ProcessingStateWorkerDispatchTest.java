package de.tum.cit.aet.artemis.lecture.service;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

/**
 * Unit tests for the pull-based worker dispatch in {@link ProcessingStateCallbackService}: claiming,
 * claim activation and lease renewal.
 */
class ProcessingStateWorkerDispatchTest {

    private static final String WORKER_BOOT_ID = "worker-boot-1";

    private ProcessingStateCallbackService callbackService;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private LectureTranscriptionRepository transcriptionRepository;

    private FeatureToggleService featureToggleService;

    private VideoSourceResolverService videoSourceResolver;

    private AttachmentVideoUnit testUnit;

    private LectureUnitProcessingState testState;

    private WebsocketMessagingService websocketMessagingService;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        transcriptionRepository = mock(LectureTranscriptionRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        websocketMessagingService = mock(WebsocketMessagingService.class);
        LectureUnitContentFingerprintService contentFingerprintService = mock(LectureUnitContentFingerprintService.class);
        when(contentFingerprintService.computeFingerprint(any())).thenReturn("v1:test-fingerprint");

        videoSourceResolver = mock(VideoSourceResolverService.class);
        featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);

        callbackService = new ProcessingStateCallbackService(processingStateRepository, transcriptionRepository, attachmentRepository,
                new ProcessingStateNotificationService(websocketMessagingService, transcriptionRepository), contentFingerprintService, featureToggleService, videoSourceResolver,
                20, 8, 3, mock(IrisLectureUnitSyncStateRepository.class));

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
    void claimReturnsPreparedScalars() {
        when(processingStateRepository.findIdleForDispatch(any(), eq(2))).thenReturn(List.of(testState));
        when(processingStateRepository.claimIdleForDispatch(eq(500L), anyString(), any(), anyInt())).thenReturn(1);

        List<ClaimedIngestionUnitDTO> claims = callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 2);

        assertThat(claims).hasSize(1);
        ClaimedIngestionUnitDTO claim = claims.getFirst();
        assertThat(claim.lectureUnitId()).isEqualTo(100L);
        assertThat(claim.contentFingerprint()).isEqualTo("v1:test-fingerprint");
        assertThat(claim.targetPhase()).isEqualTo(ProcessingPhase.INGESTING);
    }

    @Test
    void claimTranscribesOnlyASupportedVideo() {
        testUnit.setVideoSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        when(videoSourceResolver.isSupportedSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).thenReturn(true);
        when(processingStateRepository.findIdleForDispatch(any(), eq(2))).thenReturn(List.of(testState));
        when(processingStateRepository.claimIdleForDispatch(eq(500L), anyString(), any(), anyInt())).thenReturn(1);

        assertThat(callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 2)).singleElement().extracting(ClaimedIngestionUnitDTO::targetPhase).isEqualTo(ProcessingPhase.TRANSCRIBING);
    }

    @Test
    void claimIngestsAUnitWithAnUnsupportedVideoWithoutTranscribing() {
        // A link to a video page Iris cannot transcribe: the payload carries no video, so the run must not wait for a transcript
        testUnit.setVideoSource("https://example.com/lecture-recording");
        when(processingStateRepository.findIdleForDispatch(any(), eq(2))).thenReturn(List.of(testState));
        when(processingStateRepository.claimIdleForDispatch(eq(500L), anyString(), any(), anyInt())).thenReturn(1);

        assertThat(callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 2)).singleElement().extracting(ClaimedIngestionUnitDTO::targetPhase).isEqualTo(ProcessingPhase.INGESTING);
    }

    @Test
    void claimClampsRequestedJobsToTheSanityBound() {
        when(processingStateRepository.findIdleForDispatch(any(), anyInt())).thenReturn(List.of());

        callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 99);

        verify(processingStateRepository).findIdleForDispatch(any(), eq(8));
    }

    @Test
    void claimWithoutFreeSlotsClaimsNothing() {
        List<ClaimedIngestionUnitDTO> claims = callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 0);

        assertThat(claims).isEmpty();
        verify(processingStateRepository, never()).findIdleForDispatch(any(), anyInt());
        verify(processingStateRepository, never()).findStatesReadyForRetry(any(), any(), anyInt());
    }

    @Test
    void claimUnitsForWorkerHandsOutNothingWhileTheFeatureIsDisabled() {
        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(false);

        assertThat(callbackService.claimUnitsForWorker(WORKER_BOOT_ID, 2)).isEmpty();

        verify(processingStateRepository, never()).findIdleForDispatch(any(), anyInt());
    }

    @Test
    void activateClaimedJobActivatesTheClaimAtomically() {
        String claimedAt = "claim-current";
        when(processingStateRepository.activateClaimedJob(eq(100L), eq(ProcessingPhase.INGESTING), eq("token-abc"), eq("v1:test-fingerprint"), eq(WORKER_BOOT_ID), eq(claimedAt),
                any())).thenReturn(1);
        when(processingStateRepository.findByLectureUnit_Id(100L)).thenReturn(Optional.of(testState));

        assertThat(callbackService.activateClaimedJob(100L, "token-abc", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, claimedAt)).isTrue();

        verify(processingStateRepository).activateClaimedJob(eq(100L), eq(ProcessingPhase.INGESTING), eq("token-abc"), eq("v1:test-fingerprint"), eq(WORKER_BOOT_ID), eq(claimedAt),
                any());
        verify(processingStateRepository, never()).save(any());
    }

    @Test
    void activateClaimedJobStillReportsTheCommittedActivationWhenTheNotificationFails() {
        when(processingStateRepository.activateClaimedJob(eq(100L), eq(ProcessingPhase.INGESTING), eq("token-abc"), anyString(), anyString(), eq("claim-current"), any()))
                .thenReturn(1);
        when(processingStateRepository.findByLectureUnit_Id(100L)).thenThrow(new IllegalStateException("connection pool exhausted"));

        // The activation is committed before the notification runs; reporting false (or throwing) would drop a live
        // run from the worker's claim response and strand it until its lease lapses.
        assertThat(callbackService.activateClaimedJob(100L, "token-abc", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, "claim-current")).isTrue();

        verify(websocketMessagingService, never()).sendMessage(any(WebsocketDestination.class), any(Object.class));
    }

    @Test
    void activateClaimedJobIgnoresAnActivationWhoseClaimLapsed() {
        when(processingStateRepository.activateClaimedJob(anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0);

        assertThat(callbackService.activateClaimedJob(100L, "token-late", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, "claim-late")).isFalse();

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
        String staleClaimedAt = "claim-stale";
        when(processingStateRepository.activateClaimedJob(eq(100L), any(), anyString(), anyString(), anyString(), eq(staleClaimedAt), any())).thenReturn(0);

        assertThat(callbackService.activateClaimedJob(100L, "token-from-lapsed-claim", ProcessingPhase.INGESTING, "v1:test-fingerprint", WORKER_BOOT_ID, staleClaimedAt)).isFalse();

        verify(processingStateRepository, never()).save(any());
        verify(processingStateRepository, never()).findByLectureUnit_Id(anyLong());
    }

    @Test
    void renewWorkerLeasesRenewsKnownRunsAndReportsUnknownTokensRevoked() {
        testState.setPhase(ProcessingPhase.TRANSCRIBING);
        testState.setIngestionJobToken("token-known");
        testState.recordStageProgress("transcribing", 0, null);
        // A stage heartbeat committed while the renewal waited: the row now holds newer progress than the first read
        LectureUnitProcessingState freshState = new LectureUnitProcessingState(testUnit);
        freshState.setId(500L);
        freshState.setPhase(ProcessingPhase.TRANSCRIBING);
        freshState.setIngestionJobToken("token-known");
        freshState.recordStageProgress("transcribing", 1, 1);
        when(processingStateRepository.findByIngestionJobToken("token-known")).thenReturn(Optional.of(testState));
        when(processingStateRepository.findByIngestionJobToken("token-unknown")).thenReturn(Optional.empty());
        when(processingStateRepository.renewLease(eq(500L), eq("token-known"), any(), eq(WORKER_BOOT_ID))).thenReturn(1);
        when(processingStateRepository.findById(500L)).thenReturn(Optional.of(freshState));

        List<String> revoked = callbackService.renewWorkerLeases(WORKER_BOOT_ID, List.of("token-known", "token-unknown"));

        assertThat(revoked).containsExactly("token-unknown");
        verify(processingStateRepository).renewLease(eq(500L), eq("token-known"), any(), eq(WORKER_BOOT_ID));
        verify(processingStateRepository, never()).save(any());
        ArgumentCaptor<Object> pushed = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService).sendMessage(any(WebsocketDestination.class), pushed.capture());
        LectureUnitCombinedStatusDTO status = (LectureUnitCombinedStatusDTO) pushed.getValue();
        assertThat(status.stageProgress()).isEqualTo(1);
        assertThat(status.stageTotal()).isEqualTo(1);
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
    void renewWorkerLeasesKeepsRenewingTheBatchWhenAClientPushFails() {
        // The first renewal commits, then pushing it to clients fails. That must not abort the batch: every later
        // lease of this healthy worker would go unrenewed and lapse, and its revoked tokens would never be reported.
        testState.setPhase(ProcessingPhase.INGESTING);
        testState.setIngestionJobToken("token-push-fails");
        LectureUnitProcessingState laterState = new LectureUnitProcessingState(testUnit);
        laterState.setId(501L);
        laterState.setPhase(ProcessingPhase.INGESTING);
        laterState.setIngestionJobToken("token-later");
        when(processingStateRepository.findByIngestionJobToken("token-push-fails")).thenReturn(Optional.of(testState));
        when(processingStateRepository.findByIngestionJobToken("token-later")).thenReturn(Optional.of(laterState));
        when(processingStateRepository.findByIngestionJobToken("token-unknown")).thenReturn(Optional.empty());
        when(processingStateRepository.renewLease(eq(500L), eq("token-push-fails"), any(), eq(WORKER_BOOT_ID))).thenReturn(1);
        when(processingStateRepository.renewLease(eq(501L), eq("token-later"), any(), eq(WORKER_BOOT_ID))).thenReturn(1);
        when(processingStateRepository.findById(500L)).thenThrow(new RuntimeException("connection reset while re-reading the renewed row"));
        when(processingStateRepository.findById(501L)).thenReturn(Optional.of(laterState));

        List<String> revoked = callbackService.renewWorkerLeases(WORKER_BOOT_ID, List.of("token-push-fails", "token-later", "token-unknown"));

        assertThat(revoked).containsExactly("token-unknown");
        verify(processingStateRepository).renewLease(eq(501L), eq("token-later"), any(), eq(WORKER_BOOT_ID));
        // Only the later run reaches clients; the failed push is logged and skipped.
        verify(websocketMessagingService).sendMessage(any(WebsocketDestination.class), ArgumentCaptor.forClass(Object.class).capture());
    }

    @Test
    void markClaimedUnitSkippedMarksSkippedWhenTheClaimIsStillCurrent() {
        String claimedAt = "claim-current";
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
        String staleClaimedAt = "claim-stale";
        when(processingStateRepository.markSkippedIfStillClaimed(eq(100L), eq(staleClaimedAt), any())).thenReturn(0);

        assertThat(callbackService.markClaimedUnitSkipped(100L, staleClaimedAt)).isFalse();
    }

}
