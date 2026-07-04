package de.tum.cit.aet.artemis.hyperion.service.variants;

/**
 * Phases of an exercise-variant generation job (explicit state machine, plan Section 2.2).
 * The wizard's progress steps are derived 1:1 from this enum (plan Section 5.2) — it is shared
 * with the client via the regenerated OpenAPI client, so renaming a constant is a breaking change.
 *
 * Legal transitions are defined by the state diagram in plan Section 2.7.2:
 * ANALYZING → PLANNING → PROVISIONING → TRANSFORMING → VERIFYING → (REPAIRING ↔ VERIFYING)* → FINALIZING → COMPLETED,
 * with exits to FAILED (PLANNING/PROVISIONING hard failures), DRAFT_WITH_WARNINGS (budget exhausted in REPAIRING),
 * and CANCELLED (cooperative cancel, only before FINALIZING).
 */
public enum VariantJobPhase {

    ANALYZING, PLANNING, PROVISIONING, TRANSFORMING, VERIFYING, REPAIRING, FINALIZING, COMPLETED, DRAFT_WITH_WARNINGS, FAILED, CANCELLED;

    // TODO (Sonnet): Add a helper `boolean isTerminal()` returning true for COMPLETED, DRAFT_WITH_WARNINGS, FAILED,
    // CANCELLED — used by ExerciseVariantJobService to decide whether the per-exercise dedup lock can be released
    // while the job record itself is retained under TTL for the navbar tray (plan Section 5.2 "Job retention").

    // TODO (Sonnet): Add a helper `boolean isCancellable()` returning true only for phases strictly before FINALIZING
    // (ANALYZING..REPAIRING). Cancelling from FINALIZING on is rejected with 409 because the variant already exists
    // (plan Sections 5.2 "Cancellation" and 2.7.2 footnote).
}
