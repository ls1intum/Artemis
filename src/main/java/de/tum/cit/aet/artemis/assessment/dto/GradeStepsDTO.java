package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradeStepsDTO(String title, GradeType gradeType, Set<GradeStep> gradeSteps, Integer maxPoints, String plagiarismGrade, String noParticipationGrade,
        Integer presentationsNumber, Double presentationsWeight) {
}
