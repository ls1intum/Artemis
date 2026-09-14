package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;

/**
 * What the reconcile passes have actually been doing, for the ingestion dashboard.
 * <p>
 * Without this, a reconciler that runs and finds nothing is indistinguishable from one that never runs: both are
 * silence at a normal log level. The ledger sizes are shown alongside, because the missing and drift passes reason
 * about the ledger rather than about the index, so a small ledger explains a quiet reconciler.
 *
 * @param passes the per-pass position and counters, one row per pass that has ever run
 * @param ledger how many entities the sync ledger holds per type
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReconcileStatusDTO(List<ReconcilePassStateDTO> passes, List<LedgerCountDTO> ledger) {

    /**
     * One reconcile pass and where it has got to.
     *
     * @param pass               which pass this is
     * @param positionEntityType the type it is currently walking, absent before its first run
     * @param positionEntityId   how far through that type it has got
     * @param cycleStartedAt     when the current cycle began
     * @param lastRunAt          when it last ticked, which is how an operator sees it is alive at all
     * @param entitiesChecked    entities examined in the current cycle
     * @param repairsEnqueued    writes it has queued in the current cycle
     * @param rowsRemoved        index rows it has queued for deletion in the current cycle
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ReconcilePassStateDTO(String pass, String positionEntityType, Long positionEntityId, ZonedDateTime cycleStartedAt, ZonedDateTime lastRunAt, long entitiesChecked,
            long repairsEnqueued, long rowsRemoved) {

        public static ReconcilePassStateDTO of(SearchableEntityReconcileState state) {
            return new ReconcilePassStateDTO(state.getPass().name(), state.getPositionEntityType(), state.getPositionEntityId(), state.getCycleStartedAt(), state.getLastRunAt(),
                    state.getEntitiesChecked(), state.getRepairsEnqueued(), state.getRowsRemoved());
        }
    }

    /**
     * How many entities of one type the sync ledger records as written.
     *
     * @param entityType the entity type
     * @param count      the number of ledger rows
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LedgerCountDTO(String entityType, long count) {
    }
}
