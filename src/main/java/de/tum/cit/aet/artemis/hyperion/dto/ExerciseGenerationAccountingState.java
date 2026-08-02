package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How much of a generation run's provider spend the reported {@link ExerciseGenerationUsageDTO} accounts for.
 * <p>
 * Three-valued on purpose. This is a metered feature, so an administrator reconciling a provider invoice against the recorded token-usage traces must be able to tell a cost that
 * is still being accumulated from a cost that is a permanent lower bound. Artemis already models every unknown per-model price as a nullable value rather than zero for the same
 * reason; collapsing the aggregate completeness signal back into a boolean would undo that at the level where it is actually read.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
@Schema(description = "Whether the reported usage is a complete account of a generation run's provider spend")
public enum ExerciseGenerationAccountingState {

    /** The run has not been sealed yet: more provider calls may still be recorded, so the reported usage is a snapshot, not a total. */
    PENDING,

    /** The run was sealed and every admitted provider call is accounted for: the reported usage is the total. */
    COMPLETE,

    /**
     * The reported usage is a permanent lower bound and will never become complete. Reached when an admitted provider attempt could not be accounted for, when the run ended
     * without ever sealing its accounting, when the retained evidence expired, or when the caller is not entitled to the run's usage detail.
     */
    INCOMPLETE
}
