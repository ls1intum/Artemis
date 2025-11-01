package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionDTO(long id, boolean usedForTutorial, long submissionId, @Nullable String assessmentExplanation) {

    /**
     * Convert an ExampleSubmission entity to a ExampleSubmissionDTO.
     *
     * @param exampleSubmission the ExampleSubmission to convert
     * @return a DTO representation
     * @throws BadRequestAlertException if submission or submission id is missing
     */
    public static ExampleSubmissionDTO of(@NotNull ExampleSubmission exampleSubmission) {
        Long exampleSubmissionId = exampleSubmission.getId();
        if (exampleSubmissionId == null) {
            throw new BadRequestAlertException("No example submission was provided.", "exampleSubmission", "exampleSubmission.isNull");
        }
        if (exampleSubmission.getSubmission() == null) {
            throw new BadRequestAlertException("This example submission has no solution attached.", "exampleSubmission", "exampleSubmission.submissionIsNull");
        }
        Long submissionId = exampleSubmission.getSubmission().getId();
        if (submissionId == null) {
            throw new BadRequestAlertException("The submission must be saved before it can be converted.", "exampleSubmission", "exampleSubmission.submissionIdMissing");
        }
        return new ExampleSubmissionDTO(exampleSubmissionId, exampleSubmission.isUsedForTutorial(), submissionId, exampleSubmission.getAssessmentExplanation());
    }
}
