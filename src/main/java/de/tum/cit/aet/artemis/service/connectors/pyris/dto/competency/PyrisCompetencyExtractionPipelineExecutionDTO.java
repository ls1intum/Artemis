package de.tum.cit.aet.artemis.service.connectors.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;

/**
 * DTO to execute the Iris competency extraction pipeline on Pyris
 *
 * @param execution           The pipeline execution details
 * @param courseDescription   The description of the course
 * @param currentCompetencies The current competencies of the course (to avoid re-extraction)
 * @param taxonomyOptions     The taxonomy options to use
 * @param maxN                The maximum number of competencies to extract
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyExtractionPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, String courseDescription, PyrisCompetencyRecommendationDTO[] currentCompetencies,
        CompetencyTaxonomy[] taxonomyOptions, int maxN) {
}
