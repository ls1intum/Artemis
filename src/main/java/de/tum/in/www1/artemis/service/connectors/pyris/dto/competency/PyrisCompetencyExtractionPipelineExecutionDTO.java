package de.tum.in.www1.artemis.service.connectors.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;

/**
 * DTO to execute the Pyris competency extraction pipeline on Pyris
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyExtractionPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, String courseDescription, CompetencyTaxonomy[] taxonomyOptions) {
}
