package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * Hazelcast-backed job store for variant generation — mirrors {@code HyperionCodeGenerationJobService}
 * (plan Section 5.2), generalized: the job record is a rich {@link VariantJob} (phase, ChangePlan, step
 * outputs) and finished jobs are RETAINED under TTL for the navbar tray instead of being removed
 * (Section 5.2, "Job retention for the tray").
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantJobService {

    private static final String JOB_MAP_NAME = "hyperion-exercise-variant-jobs";

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    // TODO (Sonnet): TTL 24 h per plan Section 5.2 — finished jobs stay listable/deep-linkable in the tray.
    private static final int JOB_TTL_SECONDS = 24 * 3600;

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, VariantJob> jobMap;

    // TODO (Sonnet): A second small map (or map keyed by exerciseId) is needed for the per-exercise DEDUP lock,
    // because the job map is keyed by jobId and finished jobs stay in it: "Only the per-exercise dedup lock is
    // released on completion; the job record itself remains readable" (plan Section 5.2). Mirror the
    // claimJob/clearJob putIfAbsent pattern from HyperionCodeGenerationJobService for the lock map.

    public ExerciseVariantJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        // TODO (Sonnet): configure MapConfig TTL (JOB_TTL_SECONDS) exactly like HyperionCodeGenerationJobService.init(),
        // for both the job map and the dedup-lock map (lock map with a short safety TTL, e.g. the max job runtime).
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
    }

    /**
     * Claims the per-exercise slot and creates the job record.
     *
     * TODO (Sonnet): Implement per plan Sections 5.1/5.2:
     * 1. putIfAbsent on the dedup-lock map keyed by exerciseId; on conflict throw ConflictException
     * ("variantGenerationRunning") — one running job per exercise.
     * 2. Build a VariantJob (random UUID jobId, sourceExerciseId, sourceExerciseTitle, exerciseType,
     * initiatorLogin = user.getLogin(), phase = ANALYZING, attempt = 0, request, startedAt = now) and put it
     * into the job map keyed by jobId.
     * 3. Return the job; the RESOURCE dispatches it to ExerciseVariantTaskService.runJobAsync (mirroring how
     * HyperionCodeGenerationJobService.startJob dispatches, with a cleanup callback releasing ONLY the lock).
     *
     * @param user     initiating user
     * @param exercise source exercise
     * @param request  validated wizard request
     * @return the claimed job
     */
    public VariantJob startJob(User user, Exercise exercise, VariantGenerationRequestDTO request) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement startJob (plan Section 5.2)");
    }

    /**
     * TODO (Sonnet): Return the RUNNING job for the exercise if it belongs to the given user (per-user scoping
     * re-checked server-side, plan Section 5.1) — backs GET .../generate-variant/active for wizard reconnect
     * (Section 5.3, point 5).
     */
    public Optional<VariantJob> getActiveJob(User user, long exerciseId) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement getActiveJob (plan Section 5.1)");
    }

    /**
     * TODO (Sonnet): Return ALL jobs (running + retained-finished) whose initiatorLogin matches — backs
     * GET /api/hyperion/variant-jobs for the navbar tray (plan Sections 5.1 and 5.4). Order: running first,
     * then finished by finishedAt desc. Use a Hazelcast predicate/values() scan; volume is tiny (per-user, TTL'd).
     */
    public List<VariantJob> getJobsOfUser(String login) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement getJobsOfUser (plan Section 5.4)");
    }

    /**
     * TODO (Sonnet): Return the job by id if initiatorLogin matches — backs GET /api/hyperion/variant-jobs/{jobId}
     * (modal re-open in monitor mode, full step outputs, plan Section 5.4).
     */
    public Optional<VariantJob> getJob(String jobId, String login) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement getJob (plan Section 5.4)");
    }

    /**
     * TODO (Opus): Single-writer mutation API used by the pipeline; each method must read-modify-put the Hazelcast
     * entry AND publish the matching websocket event via HyperionWebsocketService on
     * "/user/topic/hyperion/variant-generation/jobs/{jobId}" (event DTO per plan Section 5.2):
     * - updatePhase(jobId, phase) → PHASE_CHANGED (+ attempt reset when entering TRANSFORMING)
     * - recordAttempt(jobId, attempt, maxAttempts, detail) → ATTEMPT ("Building solution repository — attempt 2/3")
     * - recordProgress(jobId, detail) → PROGRESS (type-specific sub-labels, Section 5.2)
     * - recordStepOutput(jobId, phase, stepOutput) → STEP_OUTPUT (expandable panels, Section 2.4)
     * - complete(jobId, variantExerciseId, warnings) → DONE (terminal COMPLETED or DRAFT_WITH_WARNINGS)
     * - fail(jobId, detail) → FAILED; cancel-side: markCancelled(jobId) → CANCELLED
     * Release the per-exercise dedup lock on every terminal transition; KEEP the job record (Section 5.2).
     */
    public void recordStepOutput(String jobId, VariantJobPhase phase, StepOutput output) {
        throw new UnsupportedOperationException("TODO (Opus): implement job mutation + event publishing (plan Section 5.2)");
    }

    /**
     * TODO (Sonnet): Cooperative cancel (plan Section 5.2): load job; verify initiatorLogin; if phase is FINALIZING
     * or terminal → throw ConflictException (409, "the variant already exists"); otherwise set cancelRequested = true
     * and put back. The PIPELINE observes the flag at phase transitions / between agent rounds and performs cleanup —
     * this method only flips the distributed flag (works regardless of which node runs the job).
     */
    public void requestCancel(String jobId, String login) {
        throw new UnsupportedOperationException("TODO (Sonnet): implement requestCancel (plan Section 5.2)");
    }
}
