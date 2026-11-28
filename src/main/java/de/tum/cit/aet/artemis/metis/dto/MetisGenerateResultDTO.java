package de.tum.cit.aet.artemis.metis.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What "generate now" did for an exercise: how many checks it created for participants without one, and how many skipped or failed checks it put back into the
 * queue. The questions themselves are generated later by the scheduling node.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisGenerateResultDTO(@NotNull int createdChecks, @NotNull int requeuedChecks) {
}
