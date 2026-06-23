package de.tum.cit.aet.artemis.programming.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for updating the build plan configuration of a programming exercise from the dedicated build plan editor.
 * It carries the structured build plan (build phases and Docker image) and the build timeout, i.e. exactly the values
 * the build plan editor controls. Immutable build config fields such as checkout paths or the branch are intentionally
 * not part of this DTO.
 *
 * @param buildPlan      the structured build plan configuration (build phases and Docker image)
 * @param timeoutSeconds the build timeout in seconds (0 means the default timeout is used)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateBuildPlanConfigurationDTO(@Valid @NotNull BuildPlanPhasesDTO buildPlan, @PositiveOrZero int timeoutSeconds) {
}
