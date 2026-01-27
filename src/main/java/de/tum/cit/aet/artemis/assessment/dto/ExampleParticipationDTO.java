package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleParticipationDTO(long id, boolean usedForTutorial, long submissionId, @Nullable String assessmentExplanation) {

    /**
     * Convert an ExampleParticipation entity to an ExampleParticipationDTO.
     *
     * @param exampleParticipation the ExampleParticipation to convert
     * @return a DTO representation
     * @throws BadRequestAlertException if submission or submission id is missing
     */
    public static ExampleParticipationDTO of(@NotNull ExampleParticipation exampleParticipation) {
        Long exampleParticipationId = exampleParticipation.getId();
        if (exampleParticipationId == null) {
            throw new BadRequestAlertException("No example participation was provided.", "exampleParticipation", "exampleParticipation.isNull");
        }
        Submission submission = exampleParticipation.getSubmission();
        if (submission == null) {
            throw new BadRequestAlertException("This example participation has no submission attached.", "exampleParticipation", "exampleParticipation.submissionIsNull");
        }
        Long submissionId = submission.getId();
        if (submissionId == null) {
            throw new BadRequestAlertException("The submission must be saved before it can be converted.", "exampleParticipation", "exampleParticipation.submissionIdMissing");
        }
        return new ExampleParticipationDTO(exampleParticipationId, exampleParticipation.isUsedForTutorial(), submissionId, exampleParticipation.getAssessmentExplanation());
    }
}
