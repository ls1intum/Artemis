package de.tum.cit.aet.artemis.assessment.dto;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;

/**
 * DTO for updating a grading scale.
 * Contains all fields needed for updating a grading scale without requiring the full entity.
 *
 * @param gradeType               the type of grading (GRADE, BONUS, NONE)
 * @param bonusStrategy           the bonus strategy to use
 * @param plagiarismGrade         the grade for plagiarism cases
 * @param noParticipationGrade    the grade for no participation
 * @param presentationsNumber     the number of presentations
 * @param presentationsWeight     the weight of presentations
 * @param gradeSteps              the grade steps for this scale (can be null or empty)
 * @param courseMaxPoints         optional: max points for the course (for course grading scales)
 * @param coursePresentationScore optional: presentation score for the course
 * @param examMaxPoints           optional: max points for the exam (for exam grading scales)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingScaleUpdateDTO(@NotNull GradeType gradeType, @Nullable BonusStrategy bonusStrategy, @Nullable @Size(max = 100) String plagiarismGrade,
        @Nullable @Size(max = 100) String noParticipationGrade, @Nullable Integer presentationsNumber, @Nullable Double presentationsWeight, @Nullable Set<GradeStepDTO> gradeSteps,
        @Nullable Integer courseMaxPoints, @Nullable Integer coursePresentationScore, @Nullable Integer examMaxPoints) {

    /**
     * Returns the grade steps, defaulting to an empty set if null.
     */
    public Set<GradeStepDTO> gradeStepsOrEmpty() {
        return gradeSteps != null ? gradeSteps : new HashSet<>();
    }

    /**
     * DTO for a grade step within a grading scale.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record GradeStepDTO(double lowerBoundPercentage, boolean lowerBoundInclusive, double upperBoundPercentage, boolean upperBoundInclusive, @NotNull String gradeName,
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
    }

    /**
     * Applies this DTO's values to an existing GradingScale entity.
     *
     * @param gradingScale the grading scale to update
     */
    public void applyTo(GradingScale gradingScale) {
        gradingScale.setGradeType(gradeType);
        gradingScale.setBonusStrategy(bonusStrategy);
        gradingScale.setPlagiarismGrade(plagiarismGrade);
        gradingScale.setNoParticipationGrade(noParticipationGrade);
        gradingScale.setPresentationsNumber(presentationsNumber);
        gradingScale.setPresentationsWeight(presentationsWeight);

        // Clear existing grade steps and add new ones
        gradingScale.getGradeSteps().clear();
        for (GradeStepDTO gradeStepDTO : gradeStepsOrEmpty()) {
            GradeStep gradeStep = gradeStepDTO.toEntity(gradingScale);
            gradingScale.getGradeSteps().add(gradeStep);
        }
    }

    /**
     * Creates a new GradingScale entity from this DTO.
     *
     * @return a new GradingScale entity
     */
    public GradingScale toEntity() {
        GradingScale gradingScale = new GradingScale();
        applyTo(gradingScale);
        return gradingScale;
    }
}

