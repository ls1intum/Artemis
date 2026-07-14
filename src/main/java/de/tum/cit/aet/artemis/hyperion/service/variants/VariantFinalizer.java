package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: FINALIZING phase — persist final state and place the variant per the wizard's
 * placement choice (plan Sections 2.3, 3/4 FINALIZING rows, 5.5). Group assignment is type-agnostic, so a
 * single shared implementation is expected to work for all types (Section 8) — keep the interface anyway so
 * a type CAN override.
 */
public interface VariantFinalizer {

    /**
     * Finalizes the variant.
     *
     * TODO (Sonnet): Implement the shared finalizer:
     * 1. Persist the final problem statement / any pending entity changes on the variant.
     * 2. Branch on placement (plan Section 5.1 PlacementDTO):
     * - EXISTING_GROUP(groupId): assign via the service-level path behind PUT .../variant-group used by
     * ExerciseVariantGroupResource — reuse, do not duplicate (Section 3 FINALIZING row).
     * - NEW_GROUP(fields): create the group via the existing group-creation endpoint's service path, then assign.
     * - STANDALONE: nothing to assign.
     * - SAME_EXAM_GROUP: exam branch — when exercise.isExamExercise(), set the exam exercise group of the SOURCE
     * on the provisioned variant instead of any course-variant-group logic; the import services already
     * support exam-context imports (Section 5.5).
     * 3. Do NOT publish the DONE websocket event here — the pipeline does that after finalize returns
     * (single event writer, Section 2.2).
     *
     * TODO (Sonnet): Finalizing must also run for DRAFT_WITH_WARNINGS outcomes — the variant is kept as a clearly
     * flagged draft, never silently deleted (plan Section 1, quality bar). Only CANCELLED/FAILED skip finalize and
     * take the cleanup path instead (Section 6).
     *
     * @param variant the verified (or draft-with-warnings) variant
     * @param job     the running job — carries the wizard request (placement choice) and the source exercise id
     *                    (a NEW_GROUP placement pulls the source into the created group as well)
     */
    void finalizeVariant(Exercise variant, VariantJob job);
}
