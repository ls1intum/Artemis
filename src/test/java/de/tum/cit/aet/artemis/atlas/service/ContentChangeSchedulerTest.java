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
 * orchestrator from accumulated batches. Verifies the feature-toggle kill switch, the empty-claim
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
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()));

        scheduler.tick();

        // The whole batch goes through a single orchestrator invocation, not one call per exercise.
        verify(orchestrationService).runBatch(COURSE_ID, exerciseIds);

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
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds))
                .thenReturn(CompetencyOrchestrationResultDTO.failed("nope", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        // A transient failure committed no mutation — the batch is requeued (without refunding the
        // daily reservation) so it retries on a later tick instead of being silently discarded.
        verify(accumulator).requeueAfterFailedRun(COURSE_ID, exerciseIds);
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        assertThat(summary.exerciseCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(0);
        assertThat(summary.failureCount()).isEqualTo(2);
    }

    @Test
    void tick_partialResult_broadcastsFailureButDoesNotRequeue() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds))
                .thenReturn(CompetencyOrchestrationResultDTO.partial("half", List.of(), CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        // Some mutations were already committed — requeueing would re-apply them, so the batch is not
        // requeued; the failure is still surfaced.
        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any());
        verify(accumulator, never()).requeueAfterConcurrentRun(anyLong(), any());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
    }

    @Test
    void tick_noOpResult_doesNotBroadcastOrRequeue() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenReturn(CompetencyOrchestrationResultDTO.noOp("nothing applicable"));

        scheduler.tick();

        // No applicable exercise was processed and nothing was discarded — no completion toast and no
        // requeue, so a deleted/exam-only batch is not reported as a fake success.
        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any());
        verify(accumulator, never()).requeueAfterConcurrentRun(anyLong(), any());
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_noBatchEligible_skipsCourse() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.empty());

        scheduler.tick();

        // Another tick (on any node) already drained the batch via the atomic claim — nothing to run.
        verify(orchestrationService, never()).runBatch(anyLong(), any());
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_inProgressResult_requeuesWholeBatchAndDoesNotBroadcast() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenReturn(CompetencyOrchestrationResultDTO.inProgress("Already running"));

        scheduler.tick();

        // A concurrent run holds the course lock — the whole batch is requeued (refunding the daily
        // reservation so retry ticks do not burn quota) and nothing is surfaced.
        verify(accumulator).requeueAfterConcurrentRun(COURSE_ID, exerciseIds);
        verify(accumulator, never()).record(anyLong(), anyLong());
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_batchThrows_broadcastsFailure() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(exerciseIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds)).thenThrow(new IllegalStateException("boom"));

        scheduler.tick();

        // An exception escapes only from batch preparation (before any mutation), so the batch is
        // requeued rather than discarded.
        verify(accumulator).requeueAfterFailedRun(COURSE_ID, exerciseIds);
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().successCount()).isEqualTo(0);
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
    }

}
