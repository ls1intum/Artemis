package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for setting the build plan of a programming exercise.
 * <p>
 * The endpoint takes this rather than the {@code BuildPlan} entity, which also carries the set of programming
 * exercises the plan belongs to. Binding a request body straight onto the entity would let a caller submit that
 * association along with the script, and the endpoint only ever reads the script.
 *
 * @param buildPlan the build plan script
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildPlanRequestDTO(String buildPlan) {
}
