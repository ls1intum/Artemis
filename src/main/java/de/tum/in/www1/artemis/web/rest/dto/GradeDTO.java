package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradeType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradeDTO {

    public String gradeName;

    public Boolean isPassingGrade;

    public GradeType gradeType;

    public GradeDTO() {
        // empty constructor for Jackson
    }

    public GradeDTO(String gradeName, Boolean isPassingGrade, GradeType gradeType) {
        this.gradeName = gradeName;
        this.isPassingGrade = isPassingGrade;
        this.gradeType = gradeType;
    }
}
