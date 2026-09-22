package de.tum.cit.aet.artemis.globalsearch.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.AggregateRoot;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Where a {@link ReconcilePass} has got to, and what it has found in the cycle it is working through, keyed
 * uniquely by {@link #pass}.
 * <p>
 * Each pass gets a bounded slice of work per tick, so progress is persisted rather than held in memory: a
 * restart must not send a pass back to the start of a corpus it is days into. The position columns differ per
 * pass because they traverse different things: MISSING walks database ids type by type, ORPHAN walks the index
 * with an opaque cursor, and DRIFT needs no position since taking the least recently verified rows is
 * self-advancing. The counters cover the current cycle only and reset when a pass wraps around.
 */
@Entity
@Table(name = "searchable_entity_reconcile_state")
@AggregateRoot("Operational log; one row per reconcile pass, not owned by anything the pass processes.")
public class SearchableEntityReconcileState extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "pass", nullable = false, unique = true, length = 32)
    private ReconcilePass pass;

    // The entity type being walked, and the highest id already examined within it (MISSING only).
    @Column(name = "position_entity_type", length = 64)
    private String positionEntityType;

    @Column(name = "position_entity_id")
    private Long positionEntityId;

    // Opaque resume token for a scan of the index (ORPHAN only).
    @Column(name = "position_cursor", length = 255)
    private String positionCursor;

    @Column(name = "cycle_started_at")
    private ZonedDateTime cycleStartedAt;

    @Column(name = "last_run_at")
    private ZonedDateTime lastRunAt;

    @Column(name = "entities_checked", nullable = false)
    private long entitiesChecked = 0;

    @Column(name = "repairs_enqueued", nullable = false)
    private long repairsEnqueued = 0;

    // Only ORPHAN ever raises this.
    @Column(name = "rows_removed", nullable = false)
    private long rowsRemoved = 0;

    /**
     * ORPHAN only: per-type count of consecutive ticks a type's orphan ratio has come back over
     * {@code orphanAbortRatio}, JSON-encoded ({@code {"course":1}}). A type is trusted to actually delete only once
     * this reaches two: one suspicious reading could be a stale scan or a bug in the eligibility check, but a
     * second one on an unrelated later tick, sampling a different slice of the type's rows, is not the same fluke
     * happening twice. Deliberately not reset by {@link #startNewCycle()}: a type's standing doesn't depend on
     * where the cursor happens to be, and a full lap is far more ticks than this needs to hold across.
     */
    @Column(name = "orphan_abort_streaks", length = 1000)
    private String orphanAbortStreaks;

    public SearchableEntityReconcileState() {
        // Default constructor for JPA
    }

    public SearchableEntityReconcileState(ReconcilePass pass) {
        this.pass = pass;
        this.cycleStartedAt = ZonedDateTime.now();
    }

    /**
     * Clears the position and counters so the pass starts a fresh traversal. Called when a pass wraps around.
     */
    public void startNewCycle() {
        this.positionEntityType = null;
        this.positionEntityId = null;
        this.positionCursor = null;
        this.entitiesChecked = 0;
        this.repairsEnqueued = 0;
        this.rowsRemoved = 0;
        this.cycleStartedAt = ZonedDateTime.now();
    }

    /**
     * Adds a single tick's work to the current cycle's totals.
     *
     * @param checked  entities or index rows examined
     * @param enqueued repairs queued onto the outbox
     * @param removed  index rows queued for removal
     */
    public void recordProgress(long checked, long enqueued, long removed) {
        this.entitiesChecked += checked;
        this.repairsEnqueued += enqueued;
        this.rowsRemoved += removed;
        this.lastRunAt = ZonedDateTime.now();
    }

    public ReconcilePass getPass() {
        return pass;
    }

    public void setPass(ReconcilePass pass) {
        this.pass = pass;
    }

    public String getPositionEntityType() {
        return positionEntityType;
    }

    public void setPositionEntityType(String positionEntityType) {
        this.positionEntityType = positionEntityType;
    }

    public Long getPositionEntityId() {
        return positionEntityId;
    }

    public void setPositionEntityId(Long positionEntityId) {
        this.positionEntityId = positionEntityId;
    }

    public String getPositionCursor() {
        return positionCursor;
    }

    public void setPositionCursor(String positionCursor) {
        this.positionCursor = positionCursor;
    }

    public ZonedDateTime getCycleStartedAt() {
        return cycleStartedAt;
    }

    public void setCycleStartedAt(ZonedDateTime cycleStartedAt) {
        this.cycleStartedAt = cycleStartedAt;
    }

    public ZonedDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(ZonedDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public long getEntitiesChecked() {
        return entitiesChecked;
    }

    public void setEntitiesChecked(long entitiesChecked) {
        this.entitiesChecked = entitiesChecked;
    }

    public long getRepairsEnqueued() {
        return repairsEnqueued;
    }

    public void setRepairsEnqueued(long repairsEnqueued) {
        this.repairsEnqueued = repairsEnqueued;
    }

    public long getRowsRemoved() {
        return rowsRemoved;
    }

    public void setRowsRemoved(long rowsRemoved) {
        this.rowsRemoved = rowsRemoved;
    }

    public String getOrphanAbortStreaks() {
        return orphanAbortStreaks;
    }

    public void setOrphanAbortStreaks(String orphanAbortStreaks) {
        this.orphanAbortStreaks = orphanAbortStreaks;
    }

    @Override
    public String toString() {
        return "SearchableEntityReconcileState{" + "id=" + getId() + ", pass=" + pass + ", positionEntityType='" + positionEntityType + '\'' + ", positionEntityId="
                + positionEntityId + ", entitiesChecked=" + entitiesChecked + ", repairsEnqueued=" + repairsEnqueued + ", rowsRemoved=" + rowsRemoved + ", lastRunAt=" + lastRunAt
                + ", orphanAbortStreaks='" + orphanAbortStreaks + '\'' + '}';
    }
}
