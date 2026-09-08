package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-round bookkeeping of the semantic repair loop: how many quality findings this round inherited from the previous one, how many disappeared, and how many are new.
 * <p>
 * A "round" is one completed quality review of a candidate, the unit a repair round is scheduled from. The counts are computed over a per-finding identity rather than by
 * category, so they distinguish the same finding recurring unrepaired from a different finding of the same category each round.
 * <p>
 * Purely observational: no scheduling decision, gate, or verdict reads these numbers.
 * <p>
 * {@link Serializable} because it is carried inside {@link ExerciseGenerationEventDTO}, which is retained in a distributed Hazelcast map for reconnect/replay.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Per-round finding bookkeeping of the semantic repair loop, so a run's transcript shows whether repairing drains findings")
public record ExerciseGenerationRepairRoundDTO(
        @Schema(description = "The 1-based index of this review round within the run", requiredMode = Schema.RequiredMode.REQUIRED) int round,
        @Schema(description = "The 1-based authoring attempt whose candidate was reviewed", requiredMode = Schema.RequiredMode.REQUIRED) int attempt,
        @Schema(description = "How many of this round's findings block acceptance", requiredMode = Schema.RequiredMode.REQUIRED) int blocking,
        @Schema(description = "How many of this round's findings are advisory", requiredMode = Schema.RequiredMode.REQUIRED) int advisory,
        @Schema(description = "How many of this round's findings were already present in the previous round", requiredMode = Schema.RequiredMode.REQUIRED) int carriedOver,
        @Schema(description = "How many of the previous round's findings are gone in this round", requiredMode = Schema.RequiredMode.REQUIRED) int drained,
        @Schema(description = "How many of this round's findings are new since the previous round", requiredMode = Schema.RequiredMode.REQUIRED) int fresh)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
