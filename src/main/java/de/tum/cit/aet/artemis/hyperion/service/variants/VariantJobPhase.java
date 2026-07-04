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

    /**
     * @return true for the four terminal phases — used to release the per-exercise dedup lock while the job
     *         record itself is retained under TTL for the navbar tray (plan Section 5.2 "Job retention")
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == DRAFT_WITH_WARNINGS || this == FAILED || this == CANCELLED;
    }

    /**
     * @return true only for phases strictly before FINALIZING — cancelling from FINALIZING on is rejected with
     *         409 because the variant already exists (plan Sections 5.2 and 2.7.2 footnote)
     */
    public boolean isCancellable() {
        return ordinal() < FINALIZING.ordinal();
    }
}
