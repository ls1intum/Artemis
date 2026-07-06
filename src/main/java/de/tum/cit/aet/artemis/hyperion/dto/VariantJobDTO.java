package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJob;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJobPhase;

/**
 * Lightweight job view for the navbar tray list, GET /api/hyperion/variant-jobs (plan Sections 5.1 and 5.4).
 * "Tray = state at a glance" — deliberately NO step outputs here; the tray stays scannable (Section 5.4).
 *
 * @param jobId                job id
 * @param sourceExerciseId     the source exercise
 * @param courseId             course of the source exercise — base of the tray's deep-link route
 * @param sourceExerciseTitle  shown as the entry label
 * @param exerciseType         drives the type-aware deep-link route (programming → exercise editor, quiz → quiz editor)
 * @param phase                current/terminal phase (spinner + progress bar derive from phase index / total)
 * @param failedInPhase        for FAILED entries: the phase the job failed in ("failed in VERIFYING", Section 5.4)
 * @param failureDetail        for FAILED entries: why the job failed — shown in the tray's summary modal
 * @param instructorSummary    for FAILED entries: AI-generated state-and-next-steps summary (null when unavailable)
 * @param attempt              repair attempt counter (shown during REPAIRING)
 * @param maxAttempts          attempt budget
 * @param variantExerciseId    set for COMPLETED/DRAFT_WITH_WARNINGS — target of the editor deep link; null for
 *                                 CANCELLED (clone cleaned up, no link, Section 5.4)
 * @param variantExerciseTitle planned variant title — the "source → variant" display; available from PLANNING on (todo-c)
 * @param warnings             non-empty for DRAFT_WITH_WARNINGS (warning badge)
 * @param startedAt            job start
 * @param finishedAt           terminal timestamp, null while running
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantJobDTO(String jobId, Long sourceExerciseId, Long courseId, String sourceExerciseTitle, ExerciseType exerciseType, VariantJobPhase phase,
        VariantJobPhase failedInPhase, String failureDetail, String instructorSummary, Integer attempt, Integer maxAttempts, Long variantExerciseId, String variantExerciseTitle,
        List<String> warnings, Instant startedAt, Instant finishedAt) implements Serializable {

    /**
     * Maps the Hazelcast job record to the tray view.
     *
     * @param job the job record
     * @return the DTO
     */
    public static VariantJobDTO of(VariantJob job) {
        return new VariantJobDTO(job.getJobId(), job.getSourceExerciseId(), job.getCourseId(), job.getSourceExerciseTitle(), job.getExerciseType(), job.getPhase(),
                job.getFailedInPhase(), job.getFailureDetail(), job.getInstructorSummary(), job.getAttempt(), job.getMaxAttempts(), job.getVariantExerciseId(),
                job.getVariantExerciseTitle(), job.getWarnings(), job.getStartedAt(), job.getFinishedAt());
    }
}
