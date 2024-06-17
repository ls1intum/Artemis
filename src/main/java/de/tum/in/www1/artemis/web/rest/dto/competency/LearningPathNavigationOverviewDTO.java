package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathNavigationOverviewDTO(List<LearningPathNavigationObjectDTO> learningObjects) {
}
