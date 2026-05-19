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
 * {@code claimDueBatch} → per-exercise orchestrator invocations prevents a concurrent tick on
 * another node from draining the same batch twice.
 * <p>
 * Adapter note: develop's {@link CompetencyOrchestrationService} exposes only the
 * single-exercise {@code run(exerciseId)} method, so this scheduler loops the claimed batch
 * sequentially rather than calling a batched entry point. Lecture-unit-only batches are claimed
 * but not orchestrated (the orchestrator does not yet accept lecture units) — the accumulator
 * still drains them so they will not re-fire after the debounce window. When the orchestrator's
 * batched signature lands, replace {@link #processBatch} with a single call.
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
        if (!featureToggleService.isFeatureEnabled(Feature.AutomaticCompetencyManagement)) {
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
        String runId = UUID.randomUUID().toString();
        if (!accumulator.tryClaimLock(courseId)) {
            log.debug("atlas.automatic course {} skipped — another scheduler tick holds the lock", courseId);
            return;
        }
        try {
            Optional<BatchClaim> maybeClaim = accumulator.claimDueBatch(courseId);
            if (maybeClaim.isEmpty()) {
                return;
            }
            BatchClaim claim = maybeClaim.get();
            if (claim.exerciseIds().isEmpty()) {
                // Lecture-unit-only batches are deferred until the orchestrator batched signature
                // lands; we still drained the accumulator so they won't re-fire on the next tick.
                if (!claim.lectureUnitIds().isEmpty()) {
                    log.debug("atlas.automatic course {} drained {} lecture unit(s) — deferred (orchestrator does not yet accept lecture units)", courseId,
                            claim.lectureUnitIds().size());
                }
                return;
            }
            processBatch(courseId, runId, claim);
        }
        finally {
            accumulator.releaseLock(courseId);
        }
    }

    private void processBatch(long courseId, String runId, BatchClaim claim) {
        int success = 0;
        int failure = 0;
        int reported = 0;
        log.info("atlas.automatic course {} firing run {} with {} exercise(s)", courseId, runId, claim.exerciseIds().size());
        for (Long exerciseId : claim.exerciseIds()) {
            try {
                CompetencyOrchestrationResultDTO result = orchestrationService.run(exerciseId);
                if (result != null && result.status() == CompetencyOrchestrationResultDTO.Status.SUCCESS) {
                    success++;
                    reported++;
                }
                else if (result != null && result.status() == CompetencyOrchestrationResultDTO.Status.IN_PROGRESS) {
                    // Concurrent course orchestration — requeue and let the next tick pick it up
                    // instead of consuming the change event as a permanent failure.
                    log.info("atlas.automatic course {} requeueing exercise {} (run {}): concurrent run in progress", courseId, exerciseId, runId);
                    accumulator.record(courseId, exerciseId, false);
                }
                else {
                    failure++;
                    reported++;
                }
            }
            catch (Exception ex) {
                failure++;
                reported++;
                log.warn("atlas.automatic per-exercise run failed exerciseId={}: {}", exerciseId, ex.getMessage());
            }
        }
        AutoOrchestrationSummaryDTO summary = new AutoOrchestrationSummaryDTO(courseId, runId, reported, success, failure, Instant.now(clock));
        websocketMessagingService.sendMessage(String.format(TOPIC_TEMPLATE, courseId), summary);
    }
}
