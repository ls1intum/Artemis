package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradeType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradeStepsDTO(String title, GradeType gradeType, Set<GradeStep> gradeSteps, Integer maxPoints) {
}
