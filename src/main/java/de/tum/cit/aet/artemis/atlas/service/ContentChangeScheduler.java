package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AutoOrchestrationSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService.BatchClaim;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

/**
 * Tick loop for the automatic competency pipeline. On every scheduled invocation the scheduler
 * asks the accumulator for courses whose debounce window has elapsed and, for each, tries to
 * acquire the scheduler-local lock before claiming the buffered batch. Holding the lock across
 * {@code claimDueBatch} → {@code runBatch} prevents a concurrent tick on another node from
 * draining the same batch twice.
 * <p>
 * The whole claimed batch is handed to {@link CompetencyOrchestrationService#runBatch} in a single
 * orchestrator invocation, so the model reasons across all changed exercises at once rather than
 * one LLM call per exercise.
 */
@Conditional(AtlasEnabled.class)
@Profile(PROFILE_SCHEDULING)
@Lazy
@Component
public class ContentChangeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContentChangeScheduler.class);

    private static final String TOPIC_TEMPLATE = "/topic/atlas/orchestrator/%d";

    private final ContentChangeAccumulatorService accumulator;

    private final CompetencyOrchestrationService orchestrationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final FeatureToggleService featureToggleService;

    private final Clock clock;

    public ContentChangeScheduler(ContentChangeAccumulatorService accumulator, CompetencyOrchestrationService orchestrationService,
            WebsocketMessagingService websocketMessagingService, FeatureToggleService featureToggleService, Clock clock) {
        this.accumulator = accumulator;
        this.orchestrationService = orchestrationService;
        this.websocketMessagingService = websocketMessagingService;
        this.featureToggleService = featureToggleService;
        this.clock = clock;
    }

    /**
     * Scheduler entry point: every {@code artemis.atlas.orchestrator.scheduler-rate-ms} milliseconds,
     * walk the accumulator for courses whose debounce window has elapsed and drive each through the
     * orchestrator under the per-course lock. A no-op when the feature toggle is disabled so the
     * toggle is a zero-cost operational kill switch.
     */
    @Scheduled(fixedRateString = "${artemis.atlas.orchestrator.scheduler-rate-ms:30000}", initialDelayString = "${artemis.atlas.orchestrator.scheduler-rate-ms:30000}")
    public void tick() {
        SecurityUtils.setAuthorizationObject();
        if (!featureToggleService.isFeatureEnabled(Feature.AtlasAgent)) {
            return;
        }
        Set<Long> dueCourses;
        try {
            dueCourses = accumulator.listDueCourseIds();
        }
        catch (Exception ex) {
            log.warn("atlas.automatic scheduler failed to list due courses: {}", ex.getMessage());
            return;
        }
        for (Long courseId : dueCourses) {
            try {
                processCourse(courseId);
            }
            catch (Exception ex) {
                log.warn("atlas.automatic scheduler failed for course {}: {}", courseId, ex.getMessage(), ex);
            }
        }
    }

    private void processCourse(long courseId) {
        // The lock-guarded claimDueBatch atomically drains and resets the bucket, so only one
        // scheduler tick — on any node — ever receives a non-empty batch for a given course. The
        // subsequent orchestration is additionally guarded by the per-course run lock in
        // CompetencyOrchestrationService, so no separate scheduler lock is needed here.
        Optional<BatchClaim> maybeClaim = accumulator.claimDueBatch(courseId);
        if (maybeClaim.isEmpty()) {
            return;
        }
        String runId = UUID.randomUUID().toString();
        processBatch(courseId, runId, maybeClaim.get());
    }

    private void processBatch(long courseId, String runId, BatchClaim claim) {
        Set<Long> exerciseIds = claim.exerciseIds();
        int exerciseCount = exerciseIds.size();
        log.info("atlas.automatic course {} firing run {} with {} exercise(s)", courseId, runId, exerciseCount);

        CompetencyOrchestrationResultDTO result;
        try {
            result = orchestrationService.runBatch(courseId, exerciseIds);
        }
        catch (Exception ex) {
            // An exception escapes runBatch only from batch preparation (exercise resolution), before
            // any competency is mutated — so the changes are safe to requeue rather than discard.
            log.warn("atlas.automatic batch run failed for course {} (run {}): {}", courseId, runId, ex.getMessage(), ex);
            accumulator.requeueAfterFailedRun(courseId, exerciseIds);
            broadcastSummary(courseId, runId, exerciseCount, false);
            return;
        }

        CompetencyOrchestrationResultDTO.Status status = result == null ? null : result.status();
        switch (status) {
            case IN_PROGRESS -> {
                // Concurrent course orchestration — requeue the whole batch and let the next tick pick
                // it up instead of consuming the change events as a permanent failure. The requeue also
                // refunds the daily-run reservation taken by claimDueBatch, so a long concurrent run
                // does not let repeated retry ticks burn the per-course cap without an actual run. No
                // completion to surface.
                log.debug("atlas.automatic course {} run {} requeued all {} exercise(s); no summary broadcast", courseId, runId, exerciseCount);
                accumulator.requeueAfterConcurrentRun(courseId, exerciseIds);
            }
            case NO_OP ->
                // Nothing was applicable (all claimed ids deleted / exam / wrong course) — nothing ran
                // and nothing was discarded, so report no completion rather than a misleading success.
                log.debug("atlas.automatic course {} run {} had no applicable exercises; no summary broadcast", courseId, runId);
            case FAILED -> {
                // The run failed before committing any mutation — requeue so the changes are retried on
                // a later tick. The daily-run reservation is kept (not refunded), so the per-course cap
                // bounds how many failed retries a day can burn.
                log.debug("atlas.automatic course {} run {} failed; requeueing {} exercise(s) for retry", courseId, runId, exerciseCount);
                accumulator.requeueAfterFailedRun(courseId, exerciseIds);
                broadcastSummary(courseId, runId, exerciseCount, false);
            }
            case SUCCESS -> broadcastSummary(courseId, runId, exerciseCount, true);
            // PARTIAL: some mutations were already committed — must NOT requeue (would re-apply). null:
            // unknown state, do not requeue. Both surface as a failure toast.
            case null, default -> broadcastSummary(courseId, runId, exerciseCount, false);
        }
    }

    private void broadcastSummary(long courseId, String runId, int exerciseCount, boolean success) {
        AutoOrchestrationSummaryDTO summary = new AutoOrchestrationSummaryDTO(courseId, runId, exerciseCount, success ? exerciseCount : 0, success ? 0 : exerciseCount,
                Instant.now(clock));
        websocketMessagingService.sendMessage(String.format(TOPIC_TEMPLATE, courseId), summary);
    }
}
