package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradeType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradeDTO(String gradeName, Boolean isPassingGrade, GradeType gradeType) {
}
