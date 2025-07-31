package de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;

/**
 * DTO to execute the Iris rewriting pipeline on Pyris
 *
 * @param execution     The pipeline execution details
 * @param toBeRewritten The text to be rewritten
 * @param courseId      The ID of the course for which the rewriting is executed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRewritingPipelineExecutionDTO(@NotNull PyrisPipelineExecutionDTO execution, @NotNull String toBeRewritten, @NotNull long courseId) {
}
