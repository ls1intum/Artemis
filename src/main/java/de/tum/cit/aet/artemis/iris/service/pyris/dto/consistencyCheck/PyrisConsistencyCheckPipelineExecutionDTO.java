package de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;

/**
 * DTO to execute the Iris consistency check pipeline on Pyris
 *
 * @param execution The pipeline execution details
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisConsistencyCheckPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, PyrisProgrammingExerciseDTO exercise) {
}
