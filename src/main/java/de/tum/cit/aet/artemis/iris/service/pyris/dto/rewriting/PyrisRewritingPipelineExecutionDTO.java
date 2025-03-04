package de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;

/**
 * DTO to execute the Iris rewriting pipeline on Pyris
 *
 * @param execution     The pipeline execution details
 * @param toBeRewritten The text to be rewritten
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRewritingPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, String toBeRewritten, Long courseId) {
}
