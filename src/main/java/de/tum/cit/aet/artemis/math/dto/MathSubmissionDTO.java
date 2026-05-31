package de.tum.cit.aet.artemis.math.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.StepDirection;

/**
 * Data Transfer Object for {@link MathSubmission}.
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
public record MathSubmissionDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, List<MathResultDTO> results, MathParticipationDTO participation,
        List<DerivationStepDTO> steps) {

    /**
     * One derivation step in the student's math.
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
    public record MathResultDTO(Long id, Double score, AssessmentType assessmentType, ZonedDateTime completionDate) {

        public static MathResultDTO of(Result result) {
            return new MathResultDTO(result.getId(), result.getScore(), result.getAssessmentType(), result.getCompletionDate());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathParticipationDTO(Long id, MathExerciseDTO exercise, String studentLogin, String studentName) {

        /**
         * Projects a {@link StudentParticipation} into a participation DTO, including the exercise stub when its
         * math-exercise association is initialized.
         *
         * @param participation the entity to project
         * @return the DTO carrying the user-facing participation fields
         */
        public static MathParticipationDTO of(StudentParticipation participation) {
            MathExerciseDTO exerciseDTO = null;
            if (Hibernate.isInitialized(participation.getExercise()) && participation.getExercise() instanceof MathExercise pe && Hibernate.isInitialized(pe.getCategories())) {
                exerciseDTO = MathExerciseDTO.of(pe);
            }
            String login = participation.getStudent().map(u -> u.getLogin()).orElse(null);
            String name = participation.getStudent().map(u -> u.getName()).orElse(null);
            return new MathParticipationDTO(participation.getId(), exerciseDTO, login, name);
        }
    }

    /**
     * Projects a {@link MathSubmission} into a DTO suitable for serialization back to the client.
     *
     * @param submission the entity to project
     * @return the DTO carrying the user-facing submission fields (results, participation, steps)
     */
    public static MathSubmissionDTO of(MathSubmission submission) {
        List<MathResultDTO> resultDTOs = null;
        List<Result> results = submission.getResults();
        if (results != null && !results.isEmpty()) {
            resultDTOs = results.stream().map(MathResultDTO::of).toList();
        }

        MathParticipationDTO participationDTO = null;
        if (submission.getParticipation() instanceof StudentParticipation sp) {
            participationDTO = MathParticipationDTO.of(sp);
        }

        List<DerivationStepDTO> stepDTOs = null;
        List<DerivationStep> steps = submission.getSteps();
        if (steps != null && !steps.isEmpty()) {
            stepDTOs = steps.stream().map(DerivationStepDTO::of).toList();
        }

        return new MathSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), resultDTOs, participationDTO, stepDTOs);
    }

    /**
     * Builds a fresh {@link MathSubmission} entity from this DTO.
     *
     * @return a new entity populated with the DTO's submission fields (id, submitted flag, steps if present)
     */
    public MathSubmission toEntity() {
        MathSubmission submission = new MathSubmission();
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
