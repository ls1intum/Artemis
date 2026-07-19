package de.tum.cit.aet.artemis.programming.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for updating the build plan configuration of a programming exercise from the dedicated build plan editor.
 * It carries the structured build plan (build phases and Docker image), the build timeout, and the Docker flags, i.e.
 * exactly the values the build plan editor controls. Immutable build config fields such as checkout paths or the branch
 * are intentionally not part of this DTO.
 *
 * @param buildPlan      the structured build plan configuration (build phases and Docker image)
 * @param timeoutSeconds the build timeout in seconds (0 means the default timeout is used)
 * @param dockerFlags    the serialized Docker flags (network, CPU, memory, environment variables), or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateBuildPlanConfigurationDTO(@Valid @NotNull BuildPlanPhasesDTO buildPlan, @PositiveOrZero int timeoutSeconds, @Nullable String dockerFlags) {
}
