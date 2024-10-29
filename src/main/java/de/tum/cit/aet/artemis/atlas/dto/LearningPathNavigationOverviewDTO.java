package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathNavigationOverviewDTO(List<LearningPathNavigationObjectDTO> learningObjects) {
}
