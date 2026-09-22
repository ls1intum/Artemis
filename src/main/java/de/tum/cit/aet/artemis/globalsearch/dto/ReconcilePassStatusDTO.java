package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;

/**
 * Where one reconcile sweep is in its current cycle.
 * <p>
 * The counters are cycle-scoped and reset when a sweep wraps around, so they answer "what has this pass
 * found since it started this cycle" rather than "ever".
 *
 * @param pass            which sweep this is
 * @param lastRunAt       when it last ticked, or null when it has never run
 * @param cycleStartedAt  when the current cycle began, or null when it has never run
 * @param entitiesChecked how many entities it has examined this cycle
 * @param repairsEnqueued how many repairs it has enqueued this cycle
 * @param rowsRemoved     how many index rows it has deleted this cycle; only the orphan pass removes rows
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReconcilePassStatusDTO(ReconcilePass pass, @Nullable ZonedDateTime lastRunAt, @Nullable ZonedDateTime cycleStartedAt, long entitiesChecked, long repairsEnqueued,
        long rowsRemoved) {

    /**
     * Projects a stored reconcile cursor onto the wire shape.
     *
     * @param state the stored cursor row
     * @return the DTO for the queue view
     */
    public static ReconcilePassStatusDTO of(SearchableEntityReconcileState state) {
        return new ReconcilePassStatusDTO(state.getPass(), state.getLastRunAt(), state.getCycleStartedAt(), state.getEntitiesChecked(), state.getRepairsEnqueued(),
                state.getRowsRemoved());
    }
}
