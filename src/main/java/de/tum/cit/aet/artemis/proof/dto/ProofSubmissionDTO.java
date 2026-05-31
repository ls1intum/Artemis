package de.tum.cit.aet.artemis.proof.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.proof.domain.DerivationStep;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.domain.StepDirection;

/**
 * Data Transfer Object for {@link ProofSubmission}.
 * Used as both request body (student sends {@code submitted}, {@code steps}) and response body.
 *
 * @param id             the submission ID (null for new submissions)
 * @param submitted      whether this is a final submission (triggers automatic grading)
 * @param submissionDate when the submission was last saved (response only)
 * @param results        automatic grading results (response only, populated after submit)
 * @param participation  the student's participation including exercise info (response only)
 * @param steps          the ordered derivation steps
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProofSubmissionDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, List<ProofResultDTO> results, ProofParticipationDTO participation,
        List<DerivationStepDTO> steps) {

    /**
     * One derivation step in the student's proof.
     *
     * @param id               persisted step ID (null for new steps)
     * @param stepIndex        position in the derivation (0-based)
     * @param appliedRuleId    rule ID from the block registry
     * @param targetNodePath   index-path to the rewritten node within the current expression tree
     * @param resultExpression the full expression tree after this step
     * @param direction        direction in which the rule was applied; {@code null} defaults to {@link StepDirection#FORWARD}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DerivationStepDTO(Long id, int stepIndex, String appliedRuleId, List<Integer> targetNodePath, MathNode resultExpression, StepDirection direction) {

        /** Convenience constructor for callers that don't care about direction (defaults to FORWARD). */
        public DerivationStepDTO(Long id, int stepIndex, String appliedRuleId, List<Integer> targetNodePath, MathNode resultExpression) {
            this(id, stepIndex, appliedRuleId, targetNodePath, resultExpression, StepDirection.FORWARD);
        }

        /**
         * @param step the entity to project
         * @return a DTO mirroring the step
         */
        public static DerivationStepDTO of(DerivationStep step) {
            return new DerivationStepDTO(step.getId(), step.getStepIndex(), step.getAppliedRuleId(), step.getTargetNodePath(), step.getResultExpression(), step.getDirection());
        }

        /**
         * @return a new {@link DerivationStep} entity populated from this DTO (id is set only if non-null)
         */
        public DerivationStep toEntity() {
            DerivationStep step = new DerivationStep();
            if (id != null) {
                step.setId(id);
            }
            step.setStepIndex(stepIndex);
            step.setAppliedRuleId(appliedRuleId);
            step.setTargetNodePath(targetNodePath);
            step.setResultExpression(resultExpression);
            step.setDirection(direction == null ? StepDirection.FORWARD : direction);
            return step;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProofResultDTO(Long id, Double score, AssessmentType assessmentType, ZonedDateTime completionDate) {

        public static ProofResultDTO of(Result result) {
            return new ProofResultDTO(result.getId(), result.getScore(), result.getAssessmentType(), result.getCompletionDate());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProofParticipationDTO(Long id, ProofExerciseDTO exercise, String studentLogin, String studentName) {

        /**
         * Projects a {@link StudentParticipation} into a participation DTO, including the exercise stub when its
         * proof-exercise association is initialized.
         *
         * @param participation the entity to project
         * @return the DTO carrying the user-facing participation fields
         */
        public static ProofParticipationDTO of(StudentParticipation participation) {
            ProofExerciseDTO exerciseDTO = null;
            if (Hibernate.isInitialized(participation.getExercise()) && participation.getExercise() instanceof ProofExercise pe && Hibernate.isInitialized(pe.getCategories())) {
                exerciseDTO = ProofExerciseDTO.of(pe);
            }
            String login = participation.getStudent().map(u -> u.getLogin()).orElse(null);
            String name = participation.getStudent().map(u -> u.getName()).orElse(null);
            return new ProofParticipationDTO(participation.getId(), exerciseDTO, login, name);
        }
    }

    /**
     * Projects a {@link ProofSubmission} into a DTO suitable for serialization back to the client.
     *
     * @param submission the entity to project
     * @return the DTO carrying the user-facing submission fields (results, participation, steps)
     */
    public static ProofSubmissionDTO of(ProofSubmission submission) {
        List<ProofResultDTO> resultDTOs = null;
        List<Result> results = submission.getResults();
        if (results != null && !results.isEmpty()) {
            resultDTOs = results.stream().map(ProofResultDTO::of).toList();
        }

        ProofParticipationDTO participationDTO = null;
        if (submission.getParticipation() instanceof StudentParticipation sp) {
            participationDTO = ProofParticipationDTO.of(sp);
        }

        List<DerivationStepDTO> stepDTOs = null;
        List<DerivationStep> steps = submission.getSteps();
        if (steps != null && !steps.isEmpty()) {
            stepDTOs = steps.stream().map(DerivationStepDTO::of).toList();
        }

        return new ProofSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), resultDTOs, participationDTO, stepDTOs);
    }

    /**
     * Builds a fresh {@link ProofSubmission} entity from this DTO.
     *
     * @return a new entity populated with the DTO's submission fields (id, submitted flag, steps if present)
     */
    public ProofSubmission toEntity() {
        ProofSubmission submission = new ProofSubmission();
        if (id != null) {
            submission.setId(id);
        }
        submission.setSubmitted(Boolean.TRUE.equals(submitted));
        if (steps != null) {
            submission.setSteps(new ArrayList<>(steps.stream().map(DerivationStepDTO::toEntity).toList()));
        }
        return submission;
    }
}
