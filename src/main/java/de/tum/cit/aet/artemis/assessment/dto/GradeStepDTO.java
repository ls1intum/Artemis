package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;

/**
 * DTO for a grade step within a grading scale.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradeStepDTO(Long id, double lowerBoundPercentage, boolean lowerBoundInclusive, double upperBoundPercentage, boolean upperBoundInclusive, @NotNull String gradeName,
        boolean isPassingGrade) {

    /**
     * Creates a GradeStep entity from this DTO.
     *
     * @param gradingScale the grading scale to associate with
     * @return a new GradeStep entity
     */
    public GradeStep toEntity(GradingScale gradingScale) {
        GradeStep gradeStep = new GradeStep();
        gradeStep.setGradingScale(gradingScale);
        gradeStep.setLowerBoundPercentage(lowerBoundPercentage);
        gradeStep.setLowerBoundInclusive(lowerBoundInclusive);
        gradeStep.setUpperBoundPercentage(upperBoundPercentage);
        gradeStep.setUpperBoundInclusive(upperBoundInclusive);
        gradeStep.setGradeName(gradeName);
        gradeStep.setIsPassingGrade(isPassingGrade);
        return gradeStep;
    }

    /**
     * Creates a {@link GradeStepDTO} from a {@link GradeStep} entity.
     *
     * @param gradeStep the grade step entity to convert
     * @return the corresponding grade step DTO
     */
    public static GradeStepDTO of(GradeStep gradeStep) {
        Objects.requireNonNull(gradeStep, "GradeStep must be set");

        return new GradeStepDTO(gradeStep.getId(), gradeStep.getLowerBoundPercentage(), gradeStep.isLowerBoundInclusive(), gradeStep.getUpperBoundPercentage(),
                gradeStep.isUpperBoundInclusive(), gradeStep.getGradeName(), gradeStep.getIsPassingGrade());
    }
}
