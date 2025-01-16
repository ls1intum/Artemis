package de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;

/**
 * DTO to execute the Iris competency extraction pipeline on Pyris
 *
 * @param execution     The pipeline execution details
 * @param toBeRephrased The text to be rephrased
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRephrasingPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, String toBeRephrased) {
}
