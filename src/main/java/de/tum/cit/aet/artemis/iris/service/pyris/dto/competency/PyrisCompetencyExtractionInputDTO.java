package de.tum.cit.aet.artemis.iris.service.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyExtractionInputDTO(String courseDescription, PyrisCompetencyRecommendationDTO[] currentCompetencies) {
}
