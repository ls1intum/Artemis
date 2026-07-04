package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantJobPhase;

/**
 * Lightweight job view for the navbar tray list, GET /api/hyperion/variant-jobs (plan Sections 5.1 and 5.4).
 * "Tray = state at a glance" — deliberately NO step outputs here; the tray stays scannable (Section 5.4).
 *
 * @param jobId               job id
 * @param sourceExerciseId    the source exercise
 * @param sourceExerciseTitle shown as the entry label
 * @param exerciseType        drives the type-aware deep-link route (programming → exercise editor, quiz → quiz editor)
 * @param phase               current/terminal phase (spinner + progress bar derive from phase index / total)
 * @param attempt             repair attempt counter (shown during REPAIRING)
 * @param maxAttempts         attempt budget
 * @param variantExerciseId   set for COMPLETED/DRAFT_WITH_WARNINGS — target of the editor deep link; null for
 *                                CANCELLED (clone cleaned up, no link, Section 5.4)
 * @param warnings            non-empty for DRAFT_WITH_WARNINGS (warning badge)
 * @param startedAt           job start
 * @param finishedAt          terminal timestamp, null while running
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantJobDTO(String jobId, Long sourceExerciseId, String sourceExerciseTitle, ExerciseType exerciseType, VariantJobPhase phase, Integer attempt, Integer maxAttempts,
        Long variantExerciseId, List<String> warnings, Instant startedAt, Instant finishedAt) implements Serializable {

    // TODO (Sonnet): Add a static factory `of(VariantJob job)` mapping from the Hazelcast record. FAILED entries
    // additionally need the failure phase for the tray label ("failed in VERIFYING", plan Section 5.4) — add a
    // `failedInPhase` component if `phase` is reused as the terminal marker.
}
