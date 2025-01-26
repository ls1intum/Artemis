package de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;

/**
 * DTO to execute the Iris consistency check pipeline on Pyris
 *
 * @param execution   The pipeline execution details
 * @param toBeChecked The text to be checked
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisConsistencycheckPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, String toBeChecked) {
}
