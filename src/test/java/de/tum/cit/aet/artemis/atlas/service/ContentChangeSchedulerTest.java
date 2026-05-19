package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * Behaviour of {@link ContentChangeScheduler} — the per-tick adapter that drives develop's
 * single-exercise orchestrator from accumulated batches. Verifies the feature-toggle kill switch,
 * the lock-collision skip, lecture-unit-only deferral, the per-exercise success/failure tally,
 * and the WebSocket completion broadcast.
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
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(false);

        scheduler.tick();

        verify(accumulator, never()).listDueCourseIds();
        verify(orchestrationService, never()).run(anyLong());
    }

    @Test
    void tick_dueCourseWithTwoExercises_runsBothAndBroadcastsSummary() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(Set.of(10L, 11L), Set.of())));
        when(orchestrationService.run(10L)).thenReturn(CompetencyOrchestrationResultDTO.success("ok 10"));
        when(orchestrationService.run(11L)).thenReturn(CompetencyOrchestrationResultDTO.failed("nope 11", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        verify(orchestrationService).run(10L);
        verify(orchestrationService).run(11L);
        verify(accumulator).releaseLock(COURSE_ID);

        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(org.mockito.ArgumentMatchers.eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        assertThat(summary.courseId()).isEqualTo(COURSE_ID);
        assertThat(summary.exerciseCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.failureCount()).isEqualTo(1);
    }

    @Test
    void tick_lockHeldByAnotherTick_skipsCourse() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(false);

        scheduler.tick();

        verify(accumulator, never()).claimDueBatch(anyLong());
        verify(orchestrationService, never()).run(anyLong());
        verify(accumulator, never()).releaseLock(anyLong());
    }

    @Test
    void tick_lectureUnitOnlyBatch_drainsButDoesNotOrchestrate() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(Set.of(), Set.of(99L))));

        scheduler.tick();

        verify(orchestrationService, never()).run(anyLong());
        verify(websocketMessagingService, never()).sendMessage(anyString(), any(AutoOrchestrationSummaryDTO.class));
        verify(accumulator).releaseLock(COURSE_ID);
    }

    @Test
    void tick_perExerciseException_countsAsFailure() {
        when(featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        when(accumulator.tryClaimLock(COURSE_ID)).thenReturn(true);
        when(accumulator.claimDueBatch(COURSE_ID)).thenReturn(Optional.of(new BatchClaim(Set.of(10L, 11L), Set.of())));
        when(orchestrationService.run(10L)).thenThrow(new IllegalStateException("boom"));
        when(orchestrationService.run(11L)).thenReturn(CompetencyOrchestrationResultDTO.success("ok"));

        scheduler.tick();

        verify(orchestrationService, times(2)).run(anyLong());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(org.mockito.ArgumentMatchers.eq("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().failureCount()).isEqualTo(1);
        assertThat(payload.getValue().successCount()).isEqualTo(1);
    }

}
