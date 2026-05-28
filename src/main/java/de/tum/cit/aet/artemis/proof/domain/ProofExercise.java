package de.tum.cit.aet.artemis.proof.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.PROOF;

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
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.proof.grader.GraderType;

/**
 * A ProofExercise.
 */
@Entity
@DiscriminatorValue(value = "R")
@SecondaryTable(name = "proof_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProofExercise extends Exercise {

    @Column(table = "proof_exercise_details", name = "description")
    private String description;

    @Convert(converter = MathNodeConverter.class)
    @Column(table = "proof_exercise_details", name = "source_expression", columnDefinition = "longtext")
    private MathNode sourceExpression;

    @Convert(converter = MathNodeConverter.class)
    @Column(table = "proof_exercise_details", name = "target_expression", columnDefinition = "longtext")
    private MathNode targetExpression;

    @Column(table = "proof_exercise_details", name = "example_solution")
    private String exampleSolution;

    @Column(table = "proof_exercise_details", name = "manual_derivation")
    private boolean manualDerivation = false;

    @Column(table = "proof_exercise_details", name = "allow_verification")
    private boolean allowVerification = true;

    @Column(table = "proof_exercise_details", name = "only_show_applicable_rules")
    private boolean onlyShowApplicableRules = false;

    @Column(table = "proof_exercise_details", name = "partial_credit_enabled")
    private boolean partialCreditEnabled = false;

    @Column(table = "proof_exercise_details", name = "ac_normalization")
    private boolean acNormalization = false;

    @Enumerated(EnumType.STRING)
    @Column(table = "proof_exercise_details", name = "grader_type", length = 32, nullable = false)
    private GraderType graderType = GraderType.REWRITE_CHAIN;

    @Enumerated(EnumType.STRING)
    @Column(table = "proof_exercise_details", name = "goal_mode", length = 16, nullable = false)
    private GoalMode goalMode = GoalMode.TRANSFORMATION;

    @Convert(converter = MathNodeConverter.class)
    @Column(table = "proof_exercise_details", name = "goal_expression", columnDefinition = "longtext")
    private MathNode goalExpression;

    @Convert(converter = ExampleDerivationsConverter.class)
    @Column(table = "proof_exercise_details", name = "example_derivations", columnDefinition = "longtext")
    private List<List<DerivationStepDTO>> exampleDerivations = Collections.emptyList();

    public boolean isManualDerivation() {
        return manualDerivation;
    }

    public void setManualDerivation(boolean manualDerivation) {
        this.manualDerivation = manualDerivation;
    }

    public boolean isAllowVerification() {
        return allowVerification;
    }

    public void setAllowVerification(boolean allowVerification) {
        this.allowVerification = allowVerification;
    }

    public boolean isOnlyShowApplicableRules() {
        return onlyShowApplicableRules;
    }

    public void setOnlyShowApplicableRules(boolean onlyShowApplicableRules) {
        this.onlyShowApplicableRules = onlyShowApplicableRules;
    }

    public boolean isPartialCreditEnabled() {
        return partialCreditEnabled;
    }

    public void setPartialCreditEnabled(boolean partialCreditEnabled) {
        this.partialCreditEnabled = partialCreditEnabled;
    }

    public boolean isAcNormalization() {
        return acNormalization;
    }

    public void setAcNormalization(boolean acNormalization) {
        this.acNormalization = acNormalization;
    }

    public GraderType getGraderType() {
        return graderType;
    }

    public void setGraderType(GraderType graderType) {
        this.graderType = graderType == null ? GraderType.REWRITE_CHAIN : graderType;
    }

    public GoalMode getGoalMode() {
        return goalMode;
    }

    public void setGoalMode(GoalMode goalMode) {
        this.goalMode = goalMode == null ? GoalMode.TRANSFORMATION : goalMode;
    }

    public MathNode getGoalExpression() {
        return goalExpression;
    }

    public void setGoalExpression(MathNode goalExpression) {
        this.goalExpression = goalExpression;
    }

    public MathNode getSourceExpression() {
        return sourceExpression;
    }

    public void setSourceExpression(MathNode sourceExpression) {
        this.sourceExpression = sourceExpression;
    }

    public MathNode getTargetExpression() {
        return targetExpression;
    }

    public void setTargetExpression(MathNode targetExpression) {
        this.targetExpression = targetExpression;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    public List<List<DerivationStepDTO>> getExampleDerivations() {
        return exampleDerivations;
    }

    public void setExampleDerivations(List<List<DerivationStepDTO>> exampleDerivations) {
        this.exampleDerivations = exampleDerivations != null ? exampleDerivations : Collections.emptyList();
    }

    @Override
    public String getType() {
        return "proof";
    }

    @Override
    public ExerciseType getExerciseType() {
        return PROOF;
    }

    @Override
    public String toString() {
        return "ProofExercise{" + "id=" + getId() + "}";
    }
}
