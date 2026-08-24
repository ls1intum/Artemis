package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: FINALIZING phase — persist final state and place the variant per the wizard's
 * placement choice. Group assignment is type-agnostic, so a single shared implementation works for all types —
 * the interface exists so a type can still override.
 */
public interface VariantFinalizer {

    /**
     * Finalizes the variant: persists pending entity changes and applies the placement choice (EXISTING_GROUP /
     * NEW_GROUP / STANDALONE / SAME_EXAM_GROUP) via the shared {@code VariantPlacementService}. Runs for both
     * COMPLETED and DRAFT_WITH_WARNINGS outcomes (a draft is kept, never silently deleted); only CANCELLED/FAILED
     * skip finalize and take the cleanup path. The DONE websocket event is published by the pipeline after this
     * returns, not here (single event writer).
     *
     * @param variant the verified (or draft-with-warnings) variant
     * @param job     the running job — carries the wizard request (placement choice) and the source exercise id
     *                    (a NEW_GROUP placement pulls the source into the created group as well)
     * @return instructor-facing warnings for placement steps that could not be carried out, empty when the
     *         placement was applied in full. A non-empty result downgrades the job to DRAFT_WITH_WARNINGS.
     */
    List<String> finalizeVariant(Exercise variant, VariantJob job);
}
