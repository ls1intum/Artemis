package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.dto.AutoOrchestrationSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService.BatchClaim;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.domain.event.LectureUnitContentChangedEvent;

/**
 * Behaviour of {@link ContentChangeScheduler} — the per-tick adapter that drives the batched
 * orchestrator from accumulated batches. Verifies the feature-toggle kill switch, the empty-claim
 * skip, the single batched orchestrator invocation, the success/failure summary mapping, the
 * full-batch requeue on a concurrent run, and the WebSocket completion broadcast.
 */
@ExtendWith(MockitoExtension.class)
class ContentChangeSchedulerTest {

    private static final long COURSE_ID = 5L;

    private static final int RESOLVED_WINDOW_SECONDS = 60;

    private static final int RESOLVED_DAILY_CAP = 3;

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private CompetencyOrchestrationService orchestrationService;

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    @Mock
    private FeatureToggleService featureToggleService;

    @Mock
    private CourseConfigurationRepository courseConfigurationRepository;

    private ContentChangeScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-24T12:00:00Z"), ZoneOffset.UTC);
        scheduler = new ContentChangeScheduler(accumulator, orchestrationService, websocketMessagingService, featureToggleService, courseConfigurationRepository, fixedClock);
    }

    /** Stub the course as auto-orchestration enabled so {@code processCourse} proceeds to claim. */
    private void stubCourseEnabled(boolean enabled) {
        CourseAutoOrchestrationConfigDTO config = new CourseAutoOrchestrationConfigDTO(enabled, null, null);
        when(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(COURSE_ID)).thenReturn(Optional.of(config));
        // The scheduler resolves the window/cap from the config once per tick and threads them into the
        // claim; stub the (real) resolution helpers on the mocked accumulator so the claim is invoked
        // with concrete resolved values.
        lenient().when(accumulator.resolveDebounceWindowSeconds(config)).thenReturn(RESOLVED_WINDOW_SECONDS);
        lenient().when(accumulator.resolveDailyCap(config)).thenReturn(RESOLVED_DAILY_CAP);
    }

    @Test
    void tick_toolLimitExceeded_doesNotReplay() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of()))
                .thenReturn(CompetencyOrchestrationResultDTO.failed("Tool budget exhausted", CompetencyOrchestrationResultDTO.FailureReason.TOOL_CALL_LIMIT_EXCEEDED));
        scheduler.tick();
        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any(), any());
        verify(accumulator, never()).requeueAfterConcurrentRun(anyLong(), any(), any());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().exerciseCount()).isEqualTo(2);
        assertThat(payload.getValue().successCount()).isEqualTo(0);
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
    }

    @Test
    void tick_incompleteCompletionExceeded_doesNotReplay() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of()))
                .thenReturn(CompetencyOrchestrationResultDTO.failed("Tool budget exhausted", CompetencyOrchestrationResultDTO.FailureReason.INCOMPLETE_ORCHESTRATION));
        scheduler.tick();
        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any(), any());
        verify(accumulator, never()).requeueAfterConcurrentRun(anyLong(), any(), any());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().exerciseCount()).isEqualTo(2);
        assertThat(payload.getValue().successCount()).isEqualTo(0);
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
    }

    @Test
    void tick_toggleDisabled_noWork() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(false);

        scheduler.tick();

        verify(accumulator, never()).listDueCourseIds();
        verify(orchestrationService, never()).runBatch(anyLong(), any(), any());
    }

    @Test
    void tick_dueCourseWithTwoExercises_runsBatchOnceAndBroadcastsSuccess() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of())).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()));

        scheduler.tick();

        // The whole batch goes through a single orchestrator invocation, not one call per exercise.
        verify(orchestrationService).runBatch(COURSE_ID, exerciseIds, Set.of());

        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        assertThat(summary.courseId()).isEqualTo(COURSE_ID);
        assertThat(summary.exerciseCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.failureCount()).isEqualTo(0);
        assertThat(summary.outcome()).isEqualTo(AutoOrchestrationSummaryDTO.Outcome.SUCCESS);
    }

    @Test
    void tick_mixedBatchWithExercisesAndLectureUnits_runsBatchOnceAndCountsAll() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        Set<Long> lectureUnitIds = Set.of(30L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, lectureUnitIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, lectureUnitIds)).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()));

        scheduler.tick();

        // Both exercises and lecture units go through a single batched orchestrator invocation.
        verify(orchestrationService).runBatch(COURSE_ID, exerciseIds, lectureUnitIds);
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        // The change count spans exercises + lecture units (2 + 1), and the success invariant still holds.
        assertThat(summary.exerciseCount()).isEqualTo(3);
        assertThat(summary.successCount()).isEqualTo(3);
        assertThat(summary.failureCount()).isEqualTo(0);
    }

    @Test
    void tick_textUnitBlankedWithinDebounceWindow_runsAndCountsOnlyTheExercise() {
        // End-to-end over the real accumulator and listener: a text unit that is edited to blank content
        // inside the debounce window must leave the queue, so the mixed batch neither passes it to the
        // orchestrator nor counts it as a processed change.
        MutableClock clock = new MutableClock(Instant.parse("2026-04-24T12:00:00Z"));
        AtlasOrchestratorProperties properties = new AtlasOrchestratorProperties("test", 1.0, "", "test", "high", false, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP, 30000L, 10);
        LectureUnitRepositoryApi lectureUnitRepositoryApi = mock(LectureUnitRepositoryApi.class);
        ContentChangeAccumulatorService realAccumulator = new ContentChangeAccumulatorService(Optional.of(new LocalDataProviderService()), clock, properties,
                courseConfigurationRepository, Optional.of(lectureUnitRepositoryApi));
        AutonomousCompetencyLectureUnitEventListener listener = new AutonomousCompetencyLectureUnitEventListener(realAccumulator, featureToggleService,
                courseConfigurationRepository, Optional.of(lectureUnitRepositoryApi));
        scheduler = new ContentChangeScheduler(realAccumulator, orchestrationService, websocketMessagingService, featureToggleService, courseConfigurationRepository, clock);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(COURSE_ID)).thenReturn(Optional.of(new CourseAutoOrchestrationConfigDTO(true, null, null)));
        TextUnit nonblank = courseTextUnit(30L, "Recursion calls itself until a base case is reached.");
        TextUnit blank = courseTextUnit(30L, "  ");
        when(lectureUnitRepositoryApi.findWithLectureById(30L)).thenReturn(Optional.of(nonblank), Optional.of(blank));
        when(orchestrationService.runBatch(COURSE_ID, Set.of(10L), Set.of())).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()));

        realAccumulator.record(COURSE_ID, 10L);
        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(nonblank));
        clock.advanceSeconds(5);
        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(blank));
        clock.advanceSeconds(RESOLVED_WINDOW_SECONDS + 1);
        scheduler.tick();

        verify(orchestrationService).runBatch(COURSE_ID, Set.of(10L), Set.of());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().exerciseCount()).isEqualTo(1);
        assertThat(payload.getValue().successCount()).isEqualTo(1);
        assertThat(payload.getValue().failureCount()).isEqualTo(0);
    }

    @Test
    void tick_textUnitBlankedDuringFailedRun_isNotRequeuedWhileExerciseRetries() {
        // End-to-end over the real accumulator and listener: the claim clears the buffered ids, so a blanking edit
        // made while the run is in flight has nothing to remove. When that run then fails, the requeue must re-check
        // the persisted unit instead of restoring it, so the retry neither passes nor counts the blank unit.
        MutableClock clock = new MutableClock(Instant.parse("2026-04-24T12:00:00Z"));
        AtlasOrchestratorProperties properties = new AtlasOrchestratorProperties("test", 1.0, "", "test", "high", false, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP, 30000L, 10);
        LectureUnitRepositoryApi lectureUnitRepositoryApi = mock(LectureUnitRepositoryApi.class);
        ContentChangeAccumulatorService realAccumulator = new ContentChangeAccumulatorService(Optional.of(new LocalDataProviderService()), clock, properties,
                courseConfigurationRepository, Optional.of(lectureUnitRepositoryApi));
        AutonomousCompetencyLectureUnitEventListener listener = new AutonomousCompetencyLectureUnitEventListener(realAccumulator, featureToggleService,
                courseConfigurationRepository, Optional.of(lectureUnitRepositoryApi));
        scheduler = new ContentChangeScheduler(realAccumulator, orchestrationService, websocketMessagingService, featureToggleService, courseConfigurationRepository, clock);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(COURSE_ID)).thenReturn(Optional.of(new CourseAutoOrchestrationConfigDTO(true, null, null)));
        TextUnit nonblank = courseTextUnit(30L, "Recursion calls itself until a base case is reached.");
        TextUnit blank = courseTextUnit(30L, " ");
        when(lectureUnitRepositoryApi.findWithLectureById(30L)).thenReturn(Optional.of(nonblank), Optional.of(blank));
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(any())).thenReturn(List.of(blank));
        when(orchestrationService.runBatch(COURSE_ID, Set.of(10L), Set.of(30L))).thenAnswer(invocation -> {
            listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(blank));
            return CompetencyOrchestrationResultDTO.failed("nope", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR);
        });
        when(orchestrationService.runBatch(COURSE_ID, Set.of(10L), Set.of())).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()));

        realAccumulator.record(COURSE_ID, 10L);
        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(nonblank));
        clock.advanceSeconds(RESOLVED_WINDOW_SECONDS + 1);
        scheduler.tick();
        clock.advanceSeconds(RESOLVED_WINDOW_SECONDS + 1);
        scheduler.tick();

        verify(orchestrationService).runBatch(COURSE_ID, Set.of(10L), Set.of(30L));
        verify(orchestrationService).runBatch(COURSE_ID, Set.of(10L), Set.of());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService, times(2)).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO retry = payload.getAllValues().get(1);
        assertThat(retry.outcome()).isEqualTo(AutoOrchestrationSummaryDTO.Outcome.SUCCESS);
        assertThat(retry.exerciseCount()).as("the retry counts only the requeued exercise").isEqualTo(1);
        // Nothing stays queued for the blank unit, so it cannot trigger a further claim.
        assertThat(realAccumulator.listDueCourseIds()).isEmpty();
    }

    @Test
    void tick_successWithDroppedLearningObjects_countsOnlyProcessedChanges() {
        // The run reports that only one of the three claimed learning objects reached the prompt (the others were
        // dropped before it, e.g. blank after flavor stripping), so the toast must not count the dropped ones.
        Set<Long> exerciseIds = Set.of(10L, 11L);
        Set<Long> lectureUnitIds = Set.of(30L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, lectureUnitIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, lectureUnitIds)).thenReturn(CompetencyOrchestrationResultDTO.success("done", List.of()).withProcessedCount(1));

        scheduler.tick();

        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().exerciseCount()).isEqualTo(1);
        assertThat(payload.getValue().successCount()).isEqualTo(1);
        assertThat(payload.getValue().failureCount()).isEqualTo(0);
    }

    @Test
    void tick_mixedBatchFailed_requeuesBothSets() {
        Set<Long> exerciseIds = Set.of(10L);
        Set<Long> lectureUnitIds = Set.of(30L, 31L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, lectureUnitIds)));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, lectureUnitIds))
                .thenReturn(CompetencyOrchestrationResultDTO.failed("nope", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        // A transient failure requeues both the exercise and the lecture-unit ids of the batch.
        verify(accumulator).requeueAfterFailedRun(COURSE_ID, exerciseIds, lectureUnitIds);
    }

    @Test
    void tick_batchFailed_broadcastsFailure() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of()))
                .thenReturn(CompetencyOrchestrationResultDTO.failed("nope", CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        // A transient failure committed no mutation — the batch is requeued (without refunding the
        // daily reservation) so it retries on a later tick instead of being silently discarded.
        verify(accumulator).requeueAfterFailedRun(COURSE_ID, exerciseIds, Set.of());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        AutoOrchestrationSummaryDTO summary = payload.getValue();
        assertThat(summary.exerciseCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(0);
        assertThat(summary.failureCount()).isEqualTo(2);
        assertThat(summary.outcome()).isEqualTo(AutoOrchestrationSummaryDTO.Outcome.FAILED);
    }

    @Test
    void tick_partialResult_broadcastsPartialButDoesNotRequeue() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of()))
                .thenReturn(CompetencyOrchestrationResultDTO.partial("half", List.of(), CompetencyOrchestrationResultDTO.FailureReason.LLM_ERROR));

        scheduler.tick();

        // Some mutations were already committed — requeueing would re-apply them, so the batch is not
        // requeued; the run is surfaced as partial rather than as a complete failure.
        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any(), any());
        verify(accumulator, never()).requeueAfterConcurrentRun(anyLong(), any(), any());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
        assertThat(payload.getValue().outcome()).isEqualTo(AutoOrchestrationSummaryDTO.Outcome.PARTIAL);
    }

    @Test
    void tick_toolLimitAfterCommittedChanges_broadcastsPartial() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of()))
                .thenReturn(CompetencyOrchestrationResultDTO.partial("Tool budget exhausted", List.of(), CompetencyOrchestrationResultDTO.FailureReason.TOOL_CALL_LIMIT_EXCEEDED));

        scheduler.tick();

        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any(), any());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().outcome()).isEqualTo(AutoOrchestrationSummaryDTO.Outcome.PARTIAL);
    }

    @Test
    void tick_noOpResult_doesNotBroadcastOrRequeue() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of())).thenReturn(CompetencyOrchestrationResultDTO.noOp("nothing applicable"));

        scheduler.tick();

        // No applicable exercise was processed and nothing was discarded — no completion toast and no
        // requeue, so a deleted/exam-only batch is not reported as a fake success.
        verify(accumulator, never()).requeueAfterFailedRun(anyLong(), any(), any());
        verify(accumulator, never()).requeueAfterConcurrentRun(anyLong(), any(), any());
        verify(websocketMessagingService, never()).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_noBatchEligible_skipsCourse() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.empty());

        scheduler.tick();

        // Another tick (on any node) already drained the batch via the atomic claim — nothing to run.
        verify(orchestrationService, never()).runBatch(anyLong(), any(), any());
        verify(websocketMessagingService, never()).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_inProgressResult_requeuesWholeBatchAndDoesNotBroadcast() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of())).thenReturn(CompetencyOrchestrationResultDTO.inProgress("Already running"));

        scheduler.tick();

        // A concurrent run holds the course lock — the whole batch is requeued (refunding the daily
        // reservation so retry ticks do not burn quota) and nothing is surfaced.
        verify(accumulator).requeueAfterConcurrentRun(COURSE_ID, exerciseIds, Set.of());
        verify(accumulator, never()).record(anyLong(), anyLong());
        verify(websocketMessagingService, never()).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    @Test
    void tick_batchThrows_broadcastsFailure() {
        Set<Long> exerciseIds = Set.of(10L, 11L);
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(true);
        when(accumulator.claimDueBatch(COURSE_ID, RESOLVED_WINDOW_SECONDS, RESOLVED_DAILY_CAP)).thenReturn(Optional.of(new BatchClaim(exerciseIds, Set.of())));
        when(orchestrationService.runBatch(COURSE_ID, exerciseIds, Set.of())).thenThrow(new IllegalStateException("boom"));

        scheduler.tick();

        // An exception escapes only from batch preparation (before any mutation), so the batch is
        // requeued rather than discarded.
        verify(accumulator).requeueAfterFailedRun(COURSE_ID, exerciseIds, Set.of());
        ArgumentCaptor<AutoOrchestrationSummaryDTO> payload = ArgumentCaptor.forClass(AutoOrchestrationSummaryDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), payload.capture());
        assertThat(payload.getValue().successCount()).isEqualTo(0);
        assertThat(payload.getValue().failureCount()).isEqualTo(2);
    }

    @Test
    void tick_courseDisabled_flushesAndDoesNotClaimOrFire() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        when(accumulator.listDueCourseIds()).thenReturn(Set.of(COURSE_ID));
        stubCourseEnabled(false);

        scheduler.tick();

        // A course that disabled auto-orchestration is skipped and its bucket is flushed; the
        // orchestrator never runs and no completion is broadcast.
        verify(accumulator).flush(COURSE_ID);
        // tick() claims via the three-argument overload (course id + resolved window + cap), so assert
        // against that signature too — a disabled-path regression that started claiming would otherwise slip past.
        verify(accumulator, never()).claimDueBatch(anyLong());
        verify(accumulator, never()).claimDueBatch(anyLong(), anyInt(), anyInt());
        verify(orchestrationService, never()).runBatch(anyLong(), any(), any());
        verify(websocketMessagingService, never()).sendMessage(topic("/topic/atlas/orchestrator/" + COURSE_ID), any(AutoOrchestrationSummaryDTO.class));
    }

    private static TextUnit courseTextUnit(long id, String content) {
        Course course = new Course();
        course.setId(COURSE_ID);
        Lecture lecture = new Lecture();
        lecture.setCourse(course);
        TextUnit unit = new TextUnit();
        unit.setId(id);
        unit.setContent(content);
        unit.setLecture(lecture);
        return unit;
    }

    /** Mutable UTC clock so the real accumulator's debounce window can elapse deterministically. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}
