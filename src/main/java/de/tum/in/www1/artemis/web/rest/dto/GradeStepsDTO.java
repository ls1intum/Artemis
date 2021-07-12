package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradeType;

public class GradeStepsDTO {

    public String title;

    public GradeType gradeType;

    public GradeStep[] gradeSteps;

    public Integer maxPoints;

    public GradeStepsDTO() {
        // empty constructor for Jackson
    }

    public GradeStepsDTO(String title, GradeType gradeType, GradeStep[] gradeSteps) {
        this.title = title;
        this.gradeType = gradeType;
        this.gradeSteps = gradeSteps;
    }

    public GradeStepsDTO(String title, GradeType gradeType, GradeStep[] gradeSteps, Integer maxPoints) {
        this.title = title;
        this.gradeType = gradeType;
        this.gradeSteps = gradeSteps;
        this.maxPoints = maxPoints;
    }

}
