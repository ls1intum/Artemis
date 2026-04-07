package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService.MAX_CONCURRENT_PROCESSING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;

/**
 * Unit tests for {@link LectureContentProcessingScheduler}.
 * Tests stuck state recovery, dispatch triggering, and backfill logic.
 */
class LectureContentProcessingSchedulerTest {

    private LectureContentProcessingScheduler scheduler;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    private LectureContentProcessingService processingService;

    private ProcessingStateCallbackService callbackService;

    private AttachmentVideoUnit testUnit;

    private LectureUnitProcessingState testState;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        attachmentVideoUnitRepository = mock(AttachmentVideoUnitTestRepository.class);
        processingService = mock(LectureContentProcessingService.class);
        callbackService = mock(ProcessingStateCallbackService.class);
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);

        when(featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)).thenReturn(true);
        when(processingService.hasProcessingCapabilities()).thenReturn(true);

        scheduler = new LectureContentProcessingScheduler(processingStateRepository, attachmentVideoUnitRepository, processingService, callbackService, featureToggleService);

        Lecture testLecture = new Lecture();
        testLecture.setId(1L);

        testUnit = new AttachmentVideoUnit();
        testUnit.setId(100L);
        testUnit.setLecture(testLecture);

        testState = new LectureUnitProcessingState(testUnit);
        testState.setId(1L);
    }

    @Nested
    class StuckStateRecovery {

        @Test
        void shouldDelegateStuckRecoveryToCallbackService() {
            // Given: A state stuck in TRANSCRIBING for too long
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should delegate to callbackService.resetToIdleForRecovery
            verify(callbackService).resetToIdleForRecovery(testState);
        }

        @Test
        void shouldNotRecoverRecentlyStartedState() {
            // Given: A state that just started (not stuck yet)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(10));
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class))).thenReturn(List.of());

            // When
            scheduler.processScheduledRetries();

            // Then: Should not attempt recovery
            verify(callbackService, never()).resetToIdleForRecovery(any());
        }

        @Test
        void shouldSkipRecoveryWhenAlreadyScheduledForRetry() {
            // Given: A stuck state that already has retryEligibleAt set
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setRetryEligibleAt(ZonedDateTime.now().plusMinutes(5)); // Already scheduled

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should skip (already scheduled)
            verify(callbackService, never()).resetToIdleForRecovery(any());
        }

        @Test
        void shouldSkipRecoveryWhenStateChangedSinceBatchRead() {
            // Given: State was TRANSCRIBING when batch was read
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            testState.setRetryEligibleAt(null);

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());

            // But when we re-fetch, the state changed to IDLE (user intervention or dispatch)
            LectureUnitProcessingState freshState = new LectureUnitProcessingState(testUnit);
            freshState.setPhase(ProcessingPhase.IDLE);
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(freshState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should NOT attempt recovery because phase changed
            verify(callbackService, never()).resetToIdleForRecovery(any());
        }
    }

    @Nested
    class DispatchTrigger {

        @Test
        void shouldCallDispatchPendingJobsAsBackup() {
            // Given: No stuck states
            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class))).thenReturn(List.of());

            // When
            scheduler.processScheduledRetries();

            // Then: Should call dispatchPendingJobs as backup trigger
            verify(callbackService).dispatchPendingJobs();
        }
    }

    @Nested
    class BackfillUnprocessedUnits {

        @Test
        void shouldSkipBackfillWhenNoProcessingCapabilities() {
            when(processingService.hasProcessingCapabilities()).thenReturn(false);

            scheduler.backfillUnprocessedUnits();

            verify(processingStateRepository, never()).countByPhaseIn(anyList());
            verify(processingService, never()).triggerProcessing(any());
        }

        @Test
        void shouldTriggerProcessingForUnprocessedUnits() {
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);

            AttachmentVideoUnit unit1 = new AttachmentVideoUnit();
            unit1.setId(101L);
            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(102L);

            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of(unit1, unit2));

            scheduler.backfillUnprocessedUnits();

            verify(processingService).triggerProcessing(unit1);
            verify(processingService).triggerProcessing(unit2);
        }

        @Test
        void shouldSkipBackfillWhenMaxConcurrentReached() {
            when(processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING))).thenReturn((long) MAX_CONCURRENT_PROCESSING);

            scheduler.backfillUnprocessedUnits();

            verify(attachmentVideoUnitRepository, never()).findUnprocessedUnitsFromActiveCourses(any(), any());
        }

        @Test
        void shouldLimitToAvailableSlots() {
            // MAX_CONCURRENT_PROCESSING is 2, so 1 active leaves 1 available slot
            when(processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING))).thenReturn(1L);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of());

            scheduler.backfillUnprocessedUnits();

            verify(attachmentVideoUnitRepository).findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class),
                    eq(org.springframework.data.domain.PageRequest.of(0, MAX_CONCURRENT_PROCESSING - 1)));
        }

        @Test
        void shouldCatchExceptionsAndContinueProcessingOtherUnits() {
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);

            AttachmentVideoUnit unit1 = new AttachmentVideoUnit();
            unit1.setId(201L);
            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(202L);
            AttachmentVideoUnit unit3 = new AttachmentVideoUnit();
            unit3.setId(203L);

            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of(unit1, unit2, unit3));
            doThrow(new RuntimeException("Processing service unavailable")).when(processingService).triggerProcessing(unit2);

            scheduler.backfillUnprocessedUnits();

            verify(processingService).triggerProcessing(unit1);
            verify(processingService).triggerProcessing(unit2);
            verify(processingService).triggerProcessing(unit3);
        }
    }
}
