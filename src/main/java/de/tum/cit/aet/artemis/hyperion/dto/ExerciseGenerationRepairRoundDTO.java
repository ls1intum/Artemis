package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-round bookkeeping of the semantic repair loop: how many quality findings this round inherited from the previous one, how many disappeared, and how many are new.
 * <p>
 * A "round" is one completed quality review of a candidate — the unit a repair round is scheduled from. Counting findings by category alone cannot tell three very different
 * pipeline behaviours apart (the same finding recurring unrepaired, a different finding of the same category each round, or a noisy reviewer), and those call for opposite fixes.
 * The counts here are computed over a per-finding identity instead, so a run's transcript answers "did repairing actually drain findings" without any log scraping.
 * <p>
 * Purely observational: no scheduling decision, gate, or verdict reads these numbers.
 * <p>
 * {@link Serializable} because it is carried inside {@link ExerciseGenerationEventDTO}, which is retained in a distributed Hazelcast map for reconnect/replay.
 *
 * @param round       the 1-based index of this review round within the run
 * @param attempt     the 1-based authoring attempt whose candidate was reviewed
 * @param blocking    how many of this round's findings block acceptance
 * @param advisory    how many of this round's findings are advisory
 * @param carriedOver how many of this round's findings were already present in the previous round (present then, still present now)
 * @param drained     how many of the previous round's findings are gone in this round (present then, absent now)
 * @param fresh       how many of this round's findings were not present in the previous round; equals the total on the first round
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Per-round finding bookkeeping of the semantic repair loop, so a run's transcript shows whether repairing drains findings")
public record ExerciseGenerationRepairRoundDTO(@Schema(description = "The 1-based index of this review round within the run") int round,
        @Schema(description = "The 1-based authoring attempt whose candidate was reviewed") int attempt,
        @Schema(description = "How many of this round's findings block acceptance") int blocking,
        @Schema(description = "How many of this round's findings are advisory") int advisory,
        @Schema(description = "How many of this round's findings were already present in the previous round") int carriedOver,
        @Schema(description = "How many of the previous round's findings are gone in this round") int drained,
        @Schema(description = "How many of this round's findings are new since the previous round") int fresh) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
