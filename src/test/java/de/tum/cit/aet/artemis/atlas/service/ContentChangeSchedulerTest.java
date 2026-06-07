package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.atlas.dto.AutoOrchestrationSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService.BatchClaim;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

/**
 * Behaviour of {@link ContentChangeScheduler} — the per-tick adapter that drives the batched
 * orchestrator from accumulated batches. Verifies the feature-toggle kill switch, the lock-collision
 * skip, the single batched orchestrator invocation, the success/failure summary mapping, the
 * full-batch requeue on a concurrent run, and the WebSocket completion broadcast.
 */
@ExtendWith(MockitoExtension.class)
class ContentChangeSchedulerTest {

    private static final long COURSE_ID = 5L;

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private CompetencyOrchestrationService orchestrationService;

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    @Mock
    private FeatureToggleService featureToggleService;

    private ContentChangeScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-24T12:00:00Z"), ZoneOffset.UTC);
        scheduler = new ContentChangeScheduler(accumulator, orchestrationService, websocketMessagingService, featureToggleService, fixedClock);
    }

    @Test
    void tick_toggleDisabled_noWork() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(false);

        scheduler.tick();

        verify(accumulator, never()).listDueCourseIds();
        verify(orchestrationService, never()).runBatch(anyLong(), any());
    }

    @Test
    void tick_dueCourseWithTwoExercises_runsBatchOnceAndBroadcastsSuccess() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()));

        scheduler.tick();

        // The whole batch goes through a single orchestrator invocation, not one call per exercise.
        verify(orchestrationService).runBatch(COURSE_ID, exerciseIds);
        verify(accumulator).releaseLock(COURSE_ID);

        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        assertThat(summary.courseId()).isEqualTo(COURSE_ID);
        assertThat(summary.exerciseCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.failureCount()).isEqualTo(0);
    }

    @Test
    void tick_batchFailed_broadcastsFailure() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds))
                .thenReturn(CompetencyOrchestrationResultDTO.failed("nope", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        assertThat(summary.exerciseCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(0);
        assertThat(summary.failureCount()).isEqualTo(2);
    }

    @Test
    void tick_lockHeldByAnotherTick_skipsCourse() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(false);

        scheduler.tick();

        verify(accumulator, never()).claimDueBatch(anyLong());
        verify(orchestrationService, never()).runBatch(anyLong(), any());
        verify(accumulator, never()).releaseLock(anyLong());
    }

    @Test
    void tick_inProgressResult_requeuesWholeBatchAndDoesNotBroadcast() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenReturn(CompetencyOrchestrationResultDTO.inProgress("Already running"));

        scheduler.tick();

        // A concurrent run holds the course lock — the whole batch is requeued and nothing is surfaced.
        verify(accumulator).record(COURSE_ID, 10L);
        verify(accumulator).record(COURSE_ID, 11L);
        verify(accumulator).releaseLock(COURSE_ID);
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_batchThrows_broadcastsFailure() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenThrow(new IllegalStateException("boom"));

        scheduler.tick();

        verify(accumulator).releaseLock(COURSE_ID);
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().successCount()).isEqualTo(0);
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
    }

}
