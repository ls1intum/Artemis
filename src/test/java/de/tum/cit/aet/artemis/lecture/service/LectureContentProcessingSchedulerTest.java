package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;
import static org.assertj.core.api.Assertions.assertThat;
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

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Unit tests for {@link LectureContentProcessingScheduler}.
 * Tests stuck state recovery and retry scheduling with exponential backoff.
 */
class LectureContentProcessingSchedulerTest {

    private LectureContentProcessingScheduler scheduler;

    private LectureUnitProcessingStateRepository processingStateRepository;

    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private LectureContentProcessingService processingService;

    private AttachmentVideoUnit testUnit;

    private LectureUnitProcessingState testState;

    @BeforeEach
    void setUp() {
        processingStateRepository = mock(LectureUnitProcessingStateRepository.class);
        attachmentVideoUnitRepository = mock(AttachmentVideoUnitRepository.class);
        processingService = mock(LectureContentProcessingService.class);

        scheduler = new LectureContentProcessingScheduler(processingStateRepository, attachmentVideoUnitRepository, processingService);

        // Set up test data
        Lecture testLecture = new Lecture();
        testLecture.setId(1L);

        testUnit = new AttachmentVideoUnit();
        testUnit.setId(100L);
        testUnit.setLecture(testLecture);

        testState = new LectureUnitProcessingState(testUnit);
    }

    @Nested
    class StuckStateRecovery {

        @Test
        void shouldDetectStuckStateWithRetryCountGreaterThanZero() {
            // Given: A state that was retried but then got stuck (no callback for 2+ hours)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1); // Already retried once
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130)); // Started 130 min ago (past 120 min timeout)

            // Only return state for TRANSCRIBING phase query
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(any(), any())).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            scheduler.processScheduledRetries();

            // Then: Should increment retry count (now 2)
            assertThat(testState.getRetryCount()).isEqualTo(2);
            // Should update startedAt to prevent re-detection
            assertThat(testState.getStartedAt()).isAfter(ZonedDateTime.now().minusSeconds(5));
            verify(processingStateRepository).save(testState);
        }

        @Test
        void shouldMarkAsFailedAfterMaxRetriesForStuckState() {
            // Given: A stuck state that has already been retried MAX_PROCESSING_RETRIES - 1 times
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(MAX_PROCESSING_RETRIES - 1); // One more will hit max
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(any(), any())).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            scheduler.processScheduledRetries();

            // Then: Should be marked as failed
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            assertThat(testState.getRetryCount()).isEqualTo(MAX_PROCESSING_RETRIES);
            assertThat(testState.getErrorKey()).isEqualTo("artemisApp.lectureUnit.processing.error.timeout");
        }

        @Test
        void shouldNotDetectRecentlyStartedStateAsStuck() {
            // Given: A state that just started (not stuck yet)
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(10)); // Only 10 min ago

            // The query should not return this state (startedAt is not past cutoff)
            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(any(), any())).thenReturn(List.of());

            // When
            scheduler.processScheduledRetries();

            // Then: State should not be modified
            assertThat(testState.getRetryCount()).isEqualTo(1); // Unchanged
            verify(processingStateRepository, never()).save(any());
        }
    }

    @Nested
    class BackoffRetry {

        @Test
        void shouldRetryStateAfterBackoffPeriod() {
            // Given: A state that failed and has waited past the backoff period
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(5)); // 5 min ago, backoff for retry 1 is 2 min
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(10)); // Not stuck (< 120 min)

            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(eq(ProcessingPhase.TRANSCRIBING), any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStatesReadyForRetry(eq(ProcessingPhase.INGESTING), any())).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should call retryTranscription
            verify(processingService).retryTranscription(testState);
        }

        @Test
        void shouldNotRetryStateBeforeBackoffPeriod() {
            // Given: A state that failed but hasn't waited long enough
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(3); // Backoff is 8 min for retry 3
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(5)); // Only 5 min ago

            // The query should not return this state (lastUpdated is not past backoff cutoff)
            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(any(), any())).thenReturn(List.of());

            // When
            scheduler.processScheduledRetries();

            // Then: Should not retry
            verify(processingService, never()).retryTranscription(any());
        }
    }

    @Nested
    class ConcurrentModification {

        @Test
        void shouldSkipRetryWhenStateChangedSinceBatchRead() {
            // Given: State was TRANSCRIBING when batch was read, but user reset it to IDLE
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.setLastUpdated(ZonedDateTime.now().minusMinutes(5));

            // Batch query returns the state
            when(processingStateRepository.findStuckStates(anyList(), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(eq(ProcessingPhase.TRANSCRIBING), any())).thenReturn(List.of(testState));
            when(processingStateRepository.findStatesReadyForRetry(eq(ProcessingPhase.INGESTING), any())).thenReturn(List.of());

            // But when we re-fetch, the state is now IDLE (user changed it)
            LectureUnitProcessingState freshState = new LectureUnitProcessingState(testUnit);
            freshState.setPhase(ProcessingPhase.IDLE); // User reset it
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(freshState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should NOT call retryTranscription because phase changed
            verify(processingService, never()).retryTranscription(any());
        }

        @Test
        void shouldSkipRecoveryWhenStateChangedSinceBatchRead() {
            // Given: State was TRANSCRIBING when batch was read, but user reset it to IDLE
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(1);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130)); // Stuck

            // Batch query returns the state
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(any(), any())).thenReturn(List.of());

            // But when we re-fetch, the state is now IDLE (user changed it)
            LectureUnitProcessingState freshState = new LectureUnitProcessingState(testUnit);
            freshState.setPhase(ProcessingPhase.IDLE); // User reset it
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(freshState));

            // When
            scheduler.processScheduledRetries();

            // Then: Should NOT save because phase changed (no modification to freshState)
            verify(processingStateRepository, never()).save(any());
        }
    }

    @Nested
    class StuckRetryScenario {

        @Test
        void shouldEventuallyFailAfterRepeatedStuckRetries() {
            // This tests the full scenario: retry gets stuck, detected, retried, gets stuck again...
            // Eventually should fail after MAX_PROCESSING_RETRIES

            // Given: State at retry 3, stuck for 130 min
            testState.setPhase(ProcessingPhase.TRANSCRIBING);
            testState.setRetryCount(3);
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));

            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.INGESTING)), any(ZonedDateTime.class))).thenReturn(List.of());
            when(processingStateRepository.findStatesReadyForRetry(any(), any())).thenReturn(List.of());
            when(processingStateRepository.findById(testState.getId())).thenReturn(Optional.of(testState));
            when(processingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When: First stuck detection
            scheduler.processScheduledRetries();

            // Then: retryCount should be 4
            assertThat(testState.getRetryCount()).isEqualTo(4);
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.TRANSCRIBING); // Not failed yet

            // Simulate another stuck scenario
            testState.setStartedAt(ZonedDateTime.now().minusMinutes(130));
            when(processingStateRepository.findStuckStates(eq(List.of(ProcessingPhase.TRANSCRIBING)), any(ZonedDateTime.class))).thenReturn(List.of(testState));

            // When: Second stuck detection
            scheduler.processScheduledRetries();

            // Then: Should now be failed (retryCount = 5 = MAX_PROCESSING_RETRIES)
            assertThat(testState.getRetryCount()).isEqualTo(5);
            assertThat(testState.getPhase()).isEqualTo(ProcessingPhase.FAILED);
            assertThat(testState.getErrorKey()).isEqualTo("artemisApp.lectureUnit.processing.error.timeout");
        }
    }

    @Nested
    class BackfillUnprocessedUnits {

        @Test
        void shouldTriggerProcessingForUnprocessedUnits() {
            // Given: Three unprocessed units from active courses, no current processing
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);

            AttachmentVideoUnit unit1 = new AttachmentVideoUnit();
            unit1.setId(101L);
            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(102L);
            AttachmentVideoUnit unit3 = new AttachmentVideoUnit();
            unit3.setId(103L);

            List<AttachmentVideoUnit> unprocessedUnits = List.of(unit1, unit2, unit3);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(unprocessedUnits);

            // When
            scheduler.backfillUnprocessedUnits();

            // Then: Should trigger processing for each unit
            verify(processingService).triggerProcessing(unit1);
            verify(processingService).triggerProcessing(unit2);
            verify(processingService).triggerProcessing(unit3);
        }

        @Test
        void shouldSkipBackfillWhenMaxConcurrentReached() {
            // Given: Already 10 units processing (max concurrent)
            when(processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING))).thenReturn(10L);

            // When
            scheduler.backfillUnprocessedUnits();

            // Then: Should not query for unprocessed units or trigger any processing
            verify(attachmentVideoUnitRepository, never()).findUnprocessedUnitsFromActiveCourses(any(), any());
            verify(processingService, never()).triggerProcessing(any());
        }

        @Test
        void shouldLimitToAvailableSlots() {
            // Given: 7 units already processing, so only 3 slots available
            when(processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING))).thenReturn(7L);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of());

            // When
            scheduler.backfillUnprocessedUnits();

            // Then: Should query with limit of 3 (available slots)
            verify(attachmentVideoUnitRepository).findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), eq(org.springframework.data.domain.PageRequest.of(0, 3)));
        }

        @Test
        void shouldHandleEmptyResultsGracefully() {
            // Given: No unprocessed units
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of());

            // When
            scheduler.backfillUnprocessedUnits();

            // Then: Should not call processingService at all
            verify(processingService, never()).triggerProcessing(any());
        }

        @Test
        void shouldCatchExceptionsAndContinueProcessingOtherUnits() {
            // Given: Three units, middle one will throw exception
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);

            AttachmentVideoUnit unit1 = new AttachmentVideoUnit();
            unit1.setId(201L);
            AttachmentVideoUnit unit2 = new AttachmentVideoUnit();
            unit2.setId(202L);
            AttachmentVideoUnit unit3 = new AttachmentVideoUnit();
            unit3.setId(203L);

            List<AttachmentVideoUnit> unprocessedUnits = List.of(unit1, unit2, unit3);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(unprocessedUnits);

            // Simulate exception on unit2
            doThrow(new RuntimeException("Processing service unavailable")).when(processingService).triggerProcessing(unit2);

            // When
            scheduler.backfillUnprocessedUnits();

            // Then: Should still process all three units (exception caught for unit2)
            verify(processingService).triggerProcessing(unit1);
            verify(processingService).triggerProcessing(unit2);
            verify(processingService).triggerProcessing(unit3);
        }

        @Test
        void shouldRequestMaxConcurrentSlotsWhenNoneProcessing() {
            // Given: No units currently processing
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of());

            // When
            scheduler.backfillUnprocessedUnits();

            // Then: Should call repository with PageRequest limiting to MAX_CONCURRENT_PROCESSING (10)
            verify(attachmentVideoUnitRepository).findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), eq(org.springframework.data.domain.PageRequest.of(0, 10)));
        }

        @Test
        void shouldPassCurrentTimeToRepository() {
            // Given: Repository should receive current time for active course filtering
            when(processingStateRepository.countByPhaseIn(anyList())).thenReturn(0L);
            when(attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(any(ZonedDateTime.class), any())).thenReturn(List.of());

            ZonedDateTime beforeCall = ZonedDateTime.now().minusSeconds(1);

            // When
            scheduler.backfillUnprocessedUnits();

            ZonedDateTime afterCall = ZonedDateTime.now().plusSeconds(1);

            // Then: Should pass a timestamp close to now
            verify(attachmentVideoUnitRepository).findUnprocessedUnitsFromActiveCourses(
                    org.mockito.ArgumentMatchers.argThat(timestamp -> timestamp.isAfter(beforeCall) && timestamp.isBefore(afterCall)), any());
        }
    }
}
