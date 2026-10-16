package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRecoveryRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

/**
 * Unit tests for {@link LectureContentProcessingScheduler}.
 * Tests stuck state recovery, the backfill, and the reconcile schedule.
 */
class LectureContentProcessingSchedulerTest {

    private LectureContentProcessingScheduler scheduler;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    private LectureContentProcessingService processingService;

    private ProcessingStateCallbackService callbackService;

    private LectureIngestionReconcileService reconcileService;

    private ProcessingStateRecoveryService recoveryService;

    private LectureUnitProcessingStateRecoveryRepository strandedRunRepository;

    private static final int BACKLOG_BUDGET = 2;

    private static final long PROCESSING_STATE_ID = 4242L;

    private static final String JOB_TOKEN = "token-abc";

    private AttachmentVideoUnit testUnit;

    private LectureUnitProcessingState testState;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        attachmentVideoUnitRepository = mock(AttachmentVideoUnitTestRepository.class);
        processingService = mock(LectureContentProcessingService.class);
        callbackService = mock(ProcessingStateCallbackService.class);
        reconcileService = mock(LectureIngestionReconcileService.class);
        recoveryService = mock(ProcessingStateRecoveryService.class);
        strandedRunRepository = mock(LectureUnitProcessingStateRecoveryRepository.class);
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);

        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);
        when(processingService.hasProcessingCapabilities()).thenReturn(true);

        scheduler = new LectureContentProcessingScheduler(processingStateRepository, attachmentVideoUnitRepository, processingService, callbackService, reconcileService,
                recoveryService, strandedRunRepository, featureToggleService, Duration.ofMinutes(30), Duration.ofMinutes(45), 20, Duration.ofSeconds(30), 12);

        Lecture testLecture = new Lecture();
        testLecture.setId(1L);

        testUnit = new AttachmentVideoUnit();
        testUnit.setId(100L);
        testUnit.setLecture(testLecture);

        testState = new LectureUnitProcessingState(testUnit);
        testState.setId(1L);
    }

    @Nested
    class ConstructorValidation {

        private LectureContentProcessingScheduler buildScheduler(Duration stallWindow, Duration slowStageWarningAfter, int noCallbackTimeoutMinutes, Duration leaseExpiry,
                int absoluteTimeoutHours) {
            return new LectureContentProcessingScheduler(processingStateRepository, attachmentVideoUnitRepository, processingService, callbackService, reconcileService,
                    recoveryService, strandedRunRepository, mock(FeatureToggleService.class), stallWindow, slowStageWarningAfter, noCallbackTimeoutMinutes, leaseExpiry,
                    absoluteTimeoutHours);
        }

        @Test
        void shouldRejectNonPositiveStallWindow() {
            assertThatThrownBy(() -> buildScheduler(Duration.ZERO, Duration.ofMinutes(45), 20, Duration.ofSeconds(30), 12)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(-1), Duration.ofMinutes(45), 20, Duration.ofSeconds(30), 12)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNonPositiveSlowStageWarningAfter() {
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(30), Duration.ZERO, 20, Duration.ofSeconds(30), 12)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNonPositiveNoCallbackTimeoutMinutes() {
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(30), Duration.ofMinutes(45), 0, Duration.ofSeconds(30), 12)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(30), Duration.ofMinutes(45), -5, Duration.ofSeconds(30), 12)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNonPositiveLeaseExpiry() {
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(30), Duration.ofMinutes(45), 20, Duration.ZERO, 12)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNonPositiveAbsoluteTimeoutHours() {
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(30), Duration.ofMinutes(45), 20, Duration.ofSeconds(30), 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> buildScheduler(Duration.ofMinutes(30), Duration.ofMinutes(45), 20, Duration.ofSeconds(30), -1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAcceptAllStrictlyPositiveThresholds() {
            LectureContentProcessingScheduler validScheduler = buildScheduler(Duration.ofMinutes(30), Duration.ofMinutes(45), 20, Duration.ofSeconds(30), 12);

            assertThat(validScheduler).isNotNull();
        }
    }

    @Nested
    class LeaseReaper {

        @Test
        void shouldReclaimTheOtherRunsAndFinishThePassWhenOneReclaimThrows() {
            // Two lapsed leases; reclaiming the first throws. The second is still reclaimed, and the pass continues.
            LectureUnitProcessingState broken = new LectureUnitProcessingState(testUnit);
            broken.setId(2L);
            broken.setPhase(ProcessingPhase.INGESTING);
            broken.setIngestionJobToken("broken-token");
            broken.setLastHeartbeatAt(ZonedDateTime.now().minusMinutes(2));
            testState.setId(PROCESSING_STATE_ID);
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(JOB_TOKEN);
            testState.setLastHeartbeatAt(ZonedDateTime.now().minusMinutes(2));

            when(processingStateRepository.findRunsWithLapsedLease(any(), any(ZonedDateTime.class))).thenReturn(List.of(broken, testState));
            when(recoveryService.reclaimLapsedLease(eq(2L), eq("broken-token"), any(), any(ZonedDateTime.class))).thenThrow(new IllegalStateException("broken row"));
            when(recoveryService.reclaimLapsedLease(eq(PROCESSING_STATE_ID), eq(JOB_TOKEN), any(), any(ZonedDateTime.class))).thenReturn(true);
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            scheduler.processScheduledRetries();

            verify(recoveryService).reclaimLapsedLease(eq(PROCESSING_STATE_ID), eq(JOB_TOKEN), any(), any(ZonedDateTime.class));
        }

        @Test
        void shouldReclaimRunWhoseLeaseLapsedWithoutSpendingRetryBudget() {
            // Given: an in-flight run whose worker stopped renewing its lease two minutes ago
            testState.setId(PROCESSING_STATE_ID);
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(JOB_TOKEN);
            testState.setRetryEligibleAt(null);
            testState.setLastHeartbeatAt(ZonedDateTime.now().minusMinutes(2));

            when(processingStateRepository.findRunsWithLapsedLease(eq(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING)), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(recoveryService.reclaimLapsedLease(eq(PROCESSING_STATE_ID), eq(JOB_TOKEN), eq(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING)),
                    any(ZonedDateTime.class))).thenReturn(true);

            // When
            scheduler.processScheduledRetries();

            // Then: reclaimed through the budget-preserving atomic reset, never through the failure path.
            // The atomicity itself (a heartbeat renewal or a completion since the batch read cancels the
            // reclaim) is enforced by the conditional UPDATE's WHERE clause, not by this service call.
            verify(recoveryService).reclaimLapsedLease(eq(PROCESSING_STATE_ID), eq(JOB_TOKEN), eq(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING)),
                    any(ZonedDateTime.class));
            verify(callbackService, never()).handleProcessingFailureIfStillLive(eq(testState), any(), any(), any());
        }

        @Test
        void shouldNotFailWhenTheAtomicReclaimFindsTheRunNoLongerLapsed() {
            // Given: the batch read found a candidate, but the atomic reclaim reports it was no longer a
            // match (a heartbeat renewed the lease, or the run completed, since the batch read)
            testState.setId(PROCESSING_STATE_ID);
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(JOB_TOKEN);
            testState.setRetryEligibleAt(null);
            testState.setLastHeartbeatAt(ZonedDateTime.now().minusMinutes(2));

            when(processingStateRepository.findRunsWithLapsedLease(eq(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING)), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(recoveryService.reclaimLapsedLease(anyLong(), anyString(), any(), any(ZonedDateTime.class))).thenReturn(false);

            // When
            scheduler.processScheduledRetries();

            // Then: nothing else treats this candidate as failed or reclaimed; the next scan re-evaluates it
            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }
    }

    @Nested
    class StuckStateRecovery {

        @Test
        void shouldResumeAContentChangeInterruptedAfterTheTokenWasInvalidated() {
            // A content change invalidated the run's token and then stopped before requeueing it, leaving the row in flight
            // with no token. Every token-matching recovery skips it, so the sweep must hand it back to the content-change path.
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(null);
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(30));
            when(strandedRunRepository.findStrandedRuns(any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(strandedRunRepository.claimStrandedRun(eq(testState.getId()), anyString(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(1);
            when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(testUnit.getId())).thenReturn(Optional.of(testUnit));

            scheduler.processScheduledRetries();

            // The recovery runs under the very claim the sweep took
            var claim = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(strandedRunRepository).claimStrandedRun(eq(testState.getId()), claim.capture(), any(ZonedDateTime.class), any(ZonedDateTime.class));
            verify(processingService).recoverInterruptedContentChange(testUnit, claim.getValue());
        }

        @Test
        void shouldNotRecoverARowThatRecoveredBetweenTheBatchReadAndTheClaim() {
            // The batch read saw the row stranded, but an edit requeued it, or another pass claimed it, before this claim: the
            // claim re-checks the stranded predicate and fails, so no recovery runs and the newer state is left alone.
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken(null);
            when(strandedRunRepository.findStrandedRuns(any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(strandedRunRepository.claimStrandedRun(eq(testState.getId()), anyString(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(0);

            scheduler.processScheduledRetries();

            verify(processingService, never()).recoverInterruptedContentChange(any(), anyString());
        }

        @Test
        void shouldNotFailAStuckRunThatAHeartbeatRefreshedSinceTheBatchRead() {
            // The batch read found the run silent past the cutoff, but a heartbeat refreshed lastUpdated before the re-read,
            // without touching phase or token. The re-read must re-check the stuck predicate instead of pinning the fresh value.
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setIngestionJobToken("token");
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(30));

            LectureUnitProcessingState refreshed = new LectureUnitProcessingState(testUnit);
            refreshed.setId(testState.getId());
            refreshed.setPhase(ProcessingPhase.TRANSCRIBING);
            refreshed.setIngestionJobToken("token");
            refreshed.setStartedAt(testState.getStartedAt());
            refreshed.setLastUpdated(ZonedDateTime.now());

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(refreshed));

            scheduler.processScheduledRetries();

            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void shouldRecoverTheOtherStuckRunsAndFinishThePassWhenOneRecoveryThrows() {
            // Two stuck runs; re-reading the first throws. The second is still recovered, and the pass continues.
            LectureUnitProcessingState broken = new LectureUnitProcessingState(testUnit);
            broken.setId(2L);
            broken.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(30)); // silent past the no-callback cutoff, as findStuckStates requires
            testState.setIngestionJobToken("stuck-run-token"); // a stuck run still holds its token; token-less rows belong to the content-change recovery
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(broken, testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(broken.getId())).thenThrow(new IllegalStateException("broken row"));
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            scheduler.processScheduledRetries();

            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, null, testState.getLastUpdated());
        }

        @Test
        void shouldDelegateStuckRecoveryToCallbackService() {
            // Given: A state stuck in TRANSCRIBING for too long
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(30)); // silent past the no-callback cutoff, as findStuckStates requires
            testState.setIngestionJobToken("stuck-run-token"); // a stuck run still holds its token; token-less rows belong to the content-change recovery
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should delegate to callbackService.handleProcessingFailureIfStillLive, pinning the
            // lastUpdated the stuck detector observed (recoverStuckState's re-fetch here returns testState
            // itself, so its own lastUpdated is what gets pinned).
            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, null, testState.getLastUpdated());
        }

        @Test
        void shouldNotRecoverRecentlyStartedState() {
            // Given: A state that just started (not stuck yet)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(10));
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            // When
            scheduler.processScheduledRetries();

            // Then: Should not attempt recovery
            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void shouldSkipRecoveryWhenAlreadyScheduledForRetry() {
            // Given: A stuck state that already has retryEligibleAt set
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setRetryEligibleAt(ZonedDateTime.now().plusMinutes(5)); // Already scheduled

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should skip (already scheduled)
            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void shouldFailStuckIngestionThroughTheChargedPath() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(30)); // silent past the no-callback cutoff, as findStuckStates requires
            testState.setIngestionJobToken("stuck-run-token"); // a stuck run still holds its token; token-less rows belong to the content-change recovery
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            scheduler.processScheduledRetries();

            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, null, testState.getLastUpdated());
        }

        @Test
        void shouldFailStuckTranscriptionThroughTheChargedPath() {
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(30)); // silent past the no-callback cutoff, as findStuckStates requires
            testState.setIngestionJobToken("stuck-run-token"); // a stuck run still holds its token; token-less rows belong to the content-change recovery
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            scheduler.processScheduledRetries();

            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, null, testState.getLastUpdated());
        }

        @Test
        void shouldSkipRecoveryWhenStateChangedSinceBatchRead() {
            // Given: State was TRANSCRIBING when batch was read
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class), any(ZonedDateTime.class)))
                    .thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            // But when we re-fetch, the state changed to IDLE (user intervention or dispatch)
            LectureUnitProcessingState freshState = new LectureUnitProcessingState(testUnit);
            freshState.setPhase(ProcessingPhase.IDLE);
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(freshState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should NOT attempt recovery because phase changed
            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }
    }

    @Nested
    class BackfillUnprocessedUnits {

        /** The reconcile service runs the backfill under its guard with the given free room, as spendBacklog does. */
        private void givenBacklogRoom(int room) {
            when(reconcileService.spendBacklog(any())).thenAnswer(invocation -> room <= 0 ? 0 : invocation.<IntUnaryOperator>getArgument(0).applyAsInt(room));
        }

        @Test
        void shouldSkipBackfillWhenNoProcessingCapabilities() {
            when(processingService.hasProcessingCapabilities()).thenReturn(false);

            scheduler.backfillUnprocessedUnits();

            verify(reconcileService, never()).spendBacklog(any());
            verify(processingService, never()).triggerProcessingAsBacklog(any());
        }

        @Test
        void shouldTriggerProcessingForUnprocessedUnits() {
            givenBacklogRoom(BACKLOG_BUDGET);

            AttachmentVideoUnit unit1 = new AttachmentVideoUnit();
            unit1.setId(101L);
            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(102L);

            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of(unit1, unit2));

            scheduler.backfillUnprocessedUnits();

            verify(processingService).triggerProcessingAsBacklog(unit1);
            verify(processingService).triggerProcessingAsBacklog(unit2);
        }

        @Test
        void shouldSkipBackfillWhenTheBacklogIsFull() {
            givenBacklogRoom(0);

            scheduler.backfillUnprocessedUnits();

            verify(attachmentVideoUnitRepository, never()).findUnprocessedUnitsFromActiveCourses(any(), any());
        }

        @Test
        void shouldLimitToAvailableSlots() {
            // Room for one more background unit
            givenBacklogRoom(1);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of());

            scheduler.backfillUnprocessedUnits();

            verify(attachmentVideoUnitRepository).findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), eq(org.springframework.data.domain.PageRequest.of(0, 1)));
        }

        @Test
        void shouldCatchExceptionsAndContinueProcessingOtherUnits() {
            givenBacklogRoom(BACKLOG_BUDGET);

            AttachmentVideoUnit unit1 = new AttachmentVideoUnit();
            unit1.setId(201L);
            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(202L);
            AttachmentVideoUnit unit3 = new AttachmentVideoUnit();
            unit3.setId(203L);

            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of(unit1, unit2, unit3));
            doThrow(new RuntimeException("Processing service unavailable")).when(processingService).triggerProcessingAsBacklog(unit2);

            scheduler.backfillUnprocessedUnits();

            verify(processingService).triggerProcessingAsBacklog(unit1);
            verify(processingService).triggerProcessingAsBacklog(unit2);
            verify(processingService).triggerProcessingAsBacklog(unit3);
        }
    }

    @Nested
    class StageLedgerLiveness {

        @Test
        void shouldFailStalledRunsWhoseHeartbeatsAreAliveButProgressFroze() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("token");
            // Heartbeats arrive (lastUpdated fresh), but the progress counter froze 40 minutes ago
            testState.recordStageProgress("vision", 41, 180);
            ReflectionTestUtils.setField(testState, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            // The stall path re-fetches and re-confirms before failing, to avoid reverting a unit
            // whose success callback landed since the batch read.
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            scheduler.processScheduledRetries();

            // failStalledState pins the lastProgressAt this decision was based on.
            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, testState.getLastProgressAt(), null);
        }

        @Test
        void shouldKeepCheckingOtherRunsAndFinishThePassWhenOneRunThrows() {
            // Two stalled runs; re-reading the first throws. The failure must stay with that row: the second run is
            // still failed for retry, and the rest of the pass (stuck recovery, dispatch) still runs.
            LectureUnitProcessingState broken = new LectureUnitProcessingState(testUnit);
            broken.setId(2L);
            broken.setPhase(ProcessingPhase.INGESTING);
            broken.recordStageProgress("vision", 41, 180);
            ReflectionTestUtils.setField(broken, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            broken.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("token");
            testState.recordStageProgress("vision", 41, 180);
            ReflectionTestUtils.setField(testState, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(broken, testState));
            when(processingStateRepository.findById(broken.getId())).thenThrow(new IllegalStateException("broken row"));
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            scheduler.processScheduledRetries();

            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, testState.getLastProgressAt(), null);
            verify(processingStateRepository, times(2)).findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class));
        }

        @Test
        void shouldFailLeasedRunsWedgedMidStageEvenWhenCallbacksWentSilent() {
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("token");
            // The worker keeps renewing its lease (fresh lastHeartbeatAt), but the pipeline wedged mid-stage:
            // no status callbacks for 40 minutes (stale lastUpdated) and the progress counter is frozen.
            // The held lease is the liveness signal here; without it this run would escape every detector.
            testState.recordStageProgress("vision", 41, 180);
            ReflectionTestUtils.setField(testState, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(40));
            testState.setLastHeartbeatAt(ZonedDateTime.now());

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            scheduler.processScheduledRetries();

            verify(callbackService).handleProcessingFailureIfStillLive(testState, null, testState.getLastProgressAt(), null);
        }

        @Test
        void shouldNotFailAStalledRunThatCompletedSinceTheBatchRead() {
            // Batch read saw it stalled and INGESTING; the re-fetch finds it already DONE
            // (a success callback landed in the window). It must not be reverted to FAILED.
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.recordStageProgress("vision", 41, 180);
            ReflectionTestUtils.setField(testState, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            LectureUnitProcessingState completed = new LectureUnitProcessingState(testUnit);
            completed.setId(1L);
            completed.setPhase(ProcessingPhase.DONE);

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(completed));

            scheduler.processScheduledRetries();

            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void reproducesStaleFailureOverwritingACompletionThatLandsAfterTheReFetch() {
            // shouldNotFailAStalledRunThatCompletedSinceTheBatchRead above covers a completion landing
            // BEFORE failStalledState's re-fetch: the re-fetch itself already sees DONE, so it correctly
            // skips. This exercises the window that re-fetch alone does NOT close: a completion landing
            // AFTER the re-fetch has already decided "still stalled" but BEFORE the failure write commits.
            // callbackService is real here (not mocked), with a repository double that acts as an
            // authoritative "backing row" so failIfStillLive's atomic guard can be observed directly.
            LectureUnitProcessingState backingRow = new LectureUnitProcessingState(testUnit);
            backingRow.setId(PROCESSING_STATE_ID);
            backingRow.setPhase(ProcessingPhase.INGESTING);
            backingRow.setIngestionJobToken(JOB_TOKEN);
            backingRow.recordStageProgress("vision", 41, 180);
            ReflectionTestUtils.setField(backingRow, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            backingRow.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            LectureUnitProcessingStateRepository raceRepository = mock(LectureUnitProcessingStateRepository.class);
            when(raceRepository.findByPhaseIn(any())).thenReturn(List.of(backingRow));
            when(raceRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(raceRepository.findRunsWithLapsedLease(any(), any())).thenReturn(List.of());
            // The re-fetch returns a detached snapshot of the pre-callback row, then — simulating a
            // terminal callback landing in the window right after this read returns — the authoritative
            // backing row is concurrently completed to DONE.
            when(raceRepository.findById(PROCESSING_STATE_ID)).thenAnswer(invocation -> {
                LectureUnitProcessingState snapshot = new LectureUnitProcessingState(testUnit);
                snapshot.setId(PROCESSING_STATE_ID);
                snapshot.setPhase(backingRow.getPhase());
                snapshot.setIngestionJobToken(backingRow.getIngestionJobToken());
                snapshot.recordStageProgress("vision", 41, 180);
                ReflectionTestUtils.setField(snapshot, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
                snapshot.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

                backingRow.transitionTo(ProcessingPhase.DONE);
                backingRow.setConfirmedFingerprint("confirmed-fp");

                return Optional.of(snapshot);
            });
            // Mimics the real UPDATE ... WHERE id AND phase AND token guard against the backing row: applies
            // the failure only while the row still matches what the caller observed at read time.
            when(raceRepository.failIfStillLiveWithProgressPin(eq(PROCESSING_STATE_ID), any(), any(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
                ProcessingPhase phase = invocation.getArgument(1);
                String token = invocation.getArgument(2);
                if (backingRow.getPhase() != phase || !Objects.equals(backingRow.getIngestionJobToken(), token)) {
                    return 0;
                }
                backingRow.setPhase(ProcessingPhase.FAILED);
                backingRow.setIngestionJobToken(null);
                return 1;
            });

            LectureTranscriptionRepository transcriptionRepository = mock(LectureTranscriptionRepository.class);
            when(transcriptionRepository.findByLectureUnit_Id(anyLong())).thenReturn(Optional.empty());

            ProcessingStateCallbackService realCallbackService = new ProcessingStateCallbackService(raceRepository, transcriptionRepository, mock(AttachmentRepository.class),
                    new ProcessingStateNotificationService(mock(WebsocketMessagingService.class), transcriptionRepository), mock(LectureUnitContentFingerprintService.class),
                    mock(FeatureToggleService.class), mock(VideoSourceResolverService.class), 20, 8, 3, mock(IrisLectureUnitSyncStateRepository.class));

            FeatureToggleService raceFeatureToggleService = mock(FeatureToggleService.class);
            when(raceFeatureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);
            LectureContentProcessingScheduler raceScheduler = new LectureContentProcessingScheduler(raceRepository, attachmentVideoUnitRepository, processingService,
                    realCallbackService, reconcileService, recoveryService, strandedRunRepository, raceFeatureToggleService, Duration.ofMinutes(30), Duration.ofMinutes(45), 20,
                    Duration.ofSeconds(30), 12);

            raceScheduler.processScheduledRetries();

            // A legitimate terminal callback completed the run to DONE in the window between
            // failStalledState's re-fetch and the failure write; that completion must survive:
            // failIfStillLive's atomic guard sees the phase no longer matches and drops the stale write.
            assertThat(backingRow.getPhase()).isEqualTo(ProcessingPhase.DONE);
        }

        @Test
        void shouldOnlyWarnAboutSlowRunsWhoseProgressKeepsMoving() {
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setIngestionJobToken("token");
            // In one stage for a long time, but the progress counter advanced recently
            testState.recordStageProgress("whisper", 10, 200);
            ReflectionTestUtils.setField(testState, "stageStartedAt", ZonedDateTime.now().minusHours(2));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            scheduler.processScheduledRetries();

            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void shouldNotFailACounterlessStageNoMatterHowLongItRunsWhileAlive() {
            // The stage was entered (lastProgressAt set once) but never reports a counter — an audit
            // stage, say. Without the guard, lastProgressAt freezes at stage entry and this run would
            // eventually read as stalled no matter how long it legitimately takes.
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setIngestionJobToken("token");
            testState.recordStageProgress("audit", null, null);
            ReflectionTestUtils.setField(testState, "lastProgressAt", ZonedDateTime.now().minusMinutes(40));
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            scheduler.processScheduledRetries();

            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void shouldIgnoreRunsWithoutStageReporting() {
            // Older Iris versions report no stage; only the plain heartbeat timeout applies
            testState.setPhase(ProcessingPhase.INGESTING);
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(1));

            when(processingStateRepository.findByPhaseIn(any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());

            scheduler.processScheduledRetries();

            verify(callbackService, never()).handleProcessingFailureIfStillLive(any(), any(), any(), any());
        }

        @Test
        void shouldReleaseAbandonedDispatchClaims() {
            when(processingStateRepository.findStuckStates(any(), any(ZonedDateTime.class), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.releaseAbandonedIdleClaims(any(), any())).thenReturn(2);

            scheduler.processScheduledRetries();

            verify(processingStateRepository).releaseAbandonedIdleClaims(any(ZonedDateTime.class), any(ZonedDateTime.class));
        }
    }

    @Nested
    class ReconcileTick {

        @Test
        void shouldStartOrContinueAPassOnEachTick() {
            when(reconcileService.startOrContinuePass()).thenReturn(3);

            scheduler.reconcileIngestionState();

            verify(reconcileService).startOrContinuePass();
        }

        @Test
        void shouldContinueTheActivePassBetweenTicks() {
            scheduler.continueIngestionReconcile();

            verify(reconcileService).continuePass();
            verify(reconcileService, never()).startOrContinuePass();
        }

        @Test
        void shouldNotContinueWhileTheFeatureIsDisabled() {
            FeatureToggleService disabledToggle = mock(FeatureToggleService.class);
            LectureContentProcessingScheduler disabledScheduler = new LectureContentProcessingScheduler(processingStateRepository, attachmentVideoUnitRepository, processingService,
                    callbackService, reconcileService, recoveryService, strandedRunRepository, disabledToggle, Duration.ofMinutes(30), Duration.ofMinutes(45), 20,
                    Duration.ofSeconds(30), 12);

            disabledScheduler.continueIngestionReconcile();

            verify(reconcileService, never()).continuePass();
        }

        @Test
        void shouldSkipReconcileWhenNoProcessingCapabilities() {
            when(processingService.hasProcessingCapabilities()).thenReturn(false);

            scheduler.reconcileIngestionState();
            scheduler.continueIngestionReconcile();

            verify(reconcileService, never()).startOrContinuePass();
            verify(reconcileService, never()).continuePass();
        }
    }
}
