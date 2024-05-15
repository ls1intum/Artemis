package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.List;

import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationDto.LearningPathNavigationObjectDto;

public record LearningPathNavigationOverviewDto(List<LearningPathNavigationObjectDto> learningObjects) {
}
