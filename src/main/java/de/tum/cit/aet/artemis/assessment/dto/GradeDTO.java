package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradeType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradeDTO(String gradeName, Boolean isPassingGrade, GradeType gradeType) {
}
