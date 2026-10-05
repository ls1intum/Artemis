package de.tum.cit.aet.artemis.hyperion.service.variants;

/**
 * Phases of an exercise-variant generation job (explicit state machine). The wizard's progress steps are
 * derived 1:1 from this enum, which is shared with the client via the OpenAPI client, so renaming a constant
 * is a breaking change.
 *
 * Legal transitions:
 * ANALYZING → PLANNING → PROVISIONING → TRANSFORMING → VERIFYING → (REPAIRING ↔ VERIFYING)* → FINALIZING → COMPLETED,
 * with exits to FAILED (PLANNING/PROVISIONING hard failures), DRAFT_WITH_WARNINGS (budget exhausted in REPAIRING),
 * and CANCELLED (cooperative cancel, only before FINALIZING).
 */
public enum VariantJobPhase {

    ANALYZING, PLANNING, PROVISIONING, TRANSFORMING, VERIFYING, REPAIRING, FINALIZING, COMPLETED, DRAFT_WITH_WARNINGS, FAILED, CANCELLED;

    /**
     * @return true for the four terminal phases — the job record is then retained under TTL for the navbar tray
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == DRAFT_WITH_WARNINGS || this == FAILED || this == CANCELLED;
    }

    /**
     * @return true only for phases strictly before FINALIZING — cancelling from FINALIZING on is rejected with
     *         409 because the variant already exists
     */
    public boolean isCancellable() {
        return ordinal() < FINALIZING.ordinal();
    }
}
