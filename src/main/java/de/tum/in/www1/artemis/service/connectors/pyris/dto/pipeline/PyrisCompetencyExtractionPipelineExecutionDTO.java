package de.tum.in.www1.artemis.service.connectors.pyris.dto.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyExtractionPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, String courseDescription, CompetencyTaxonomy[] taxonomyOptions) {
}
