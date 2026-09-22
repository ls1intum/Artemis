package de.tum.cit.aet.artemis.globalsearch.domain;

/**
 * The three passes that keep the {@code SearchableEntities} index in step with the database.
 * <p>
 * None of them writes to Weaviate. Each detects a different kind of divergence and queues the repair onto the
 * outbox, so every write stays on the single-writer path the dispatcher owns.
 * <p>
 * They are scheduled, budgeted and enabled independently, because they cost different things and carry
 * different risk: one is a cheap identity comparison, one loads entities, and one can delete.
 */
public enum ReconcilePass {

    /**
     * Finds entities the index has never confirmed holding, including everything that predates the outbox.
     * Compares identity only, so it never loads an entity.
     */
    MISSING,

    /**
     * Re-derives entities that were confirmed written and checks whether their content still matches. This is
     * the expensive pass, since checking an entity means loading it.
     */
    DRIFT,

    /**
     * Scans the index itself, removing rows whose entity is gone and repairing rows whose stored content
     * disagrees with what was last written. The only pass that deletes.
     */
    ORPHAN
}
