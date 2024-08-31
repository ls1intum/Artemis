package de.tum.in.www1.artemis.service.connectors.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyExtractionInputDTO(String courseDescription, PyrisCompetencyRecommendationDTO[] currentCompetencies) {
}
