package de.tum.cit.aet.artemis.math.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.MATH;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import lombok.Getter;
import lombok.Setter;

/**
 * A MathExercise.
 */
@Getter
@Entity
@DiscriminatorValue(value = "R")
@SecondaryTable(name = "math_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExercise extends Exercise {

    @Setter
    @Column(table = "math_exercise_details", name = "description")
    private String description;

    @Setter
    @Convert(converter = MathNodeConverter.class)
    @Column(table = "math_exercise_details", name = "source_expression", columnDefinition = "longtext")
    private MathNode sourceExpression;

    @Setter
    @Convert(converter = MathNodeConverter.class)
    @Column(table = "math_exercise_details", name = "target_expression", columnDefinition = "longtext")
    private MathNode targetExpression;

    @Setter
    @Column(table = "math_exercise_details", name = "example_solution")
    private String exampleSolution;

    @Setter
    @Column(table = "math_exercise_details", name = "manual_derivation")
    private boolean manualDerivation = false;

    @Setter
    @Column(table = "math_exercise_details", name = "allow_verification")
    private boolean allowVerification = true;

    @Setter
    @Column(table = "math_exercise_details", name = "only_show_applicable_rules")
    private boolean onlyShowApplicableRules = false;

    @Setter
    @Column(table = "math_exercise_details", name = "partial_credit_enabled")
    private boolean partialCreditEnabled = false;

    @Setter
    @Column(table = "math_exercise_details", name = "ac_normalization")
    private boolean acNormalization = false;

    @Enumerated(EnumType.STRING)
    @Column(table = "math_exercise_details", name = "grader_type", length = 32, nullable = false)
    private GraderType graderType = GraderType.REWRITE_CHAIN;

    @Enumerated(EnumType.STRING)
    @Column(table = "math_exercise_details", name = "goal_mode", length = 16, nullable = false)
    private GoalMode goalMode = GoalMode.TRANSFORMATION;

    @Setter
    @Convert(converter = MathNodeConverter.class)
    @Column(table = "math_exercise_details", name = "goal_expression", columnDefinition = "longtext")
    private MathNode goalExpression;

    @Convert(converter = ExampleDerivationsConverter.class)
    @Column(table = "math_exercise_details", name = "example_derivations", columnDefinition = "longtext")
    private List<List<DerivationStepDTO>> exampleDerivations = Collections.emptyList();

    public void setGraderType(GraderType graderType) {
        this.graderType = graderType == null ? GraderType.REWRITE_CHAIN : graderType;
    }

    public void setGoalMode(GoalMode goalMode) {
        this.goalMode = goalMode == null ? GoalMode.TRANSFORMATION : goalMode;
    }

    public void setExampleDerivations(List<List<DerivationStepDTO>> exampleDerivations) {
        this.exampleDerivations = exampleDerivations != null ? exampleDerivations : Collections.emptyList();
    }

    @Override
    public String getType() {
        return "math";
    }

    @Override
    public ExerciseType getExerciseType() {
        return MATH;
    }

    @Override
    public String toString() {
        return "MathExercise{" + "id=" + getId() + "}";
    }
}
