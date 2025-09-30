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
            throw new BadRequestAlertException("ExampleSubmission cannot be null", "exampleSubmission", "exampleSubmission.isNull");
        }
        if (exampleSubmission.getSubmission() == null) {
            throw new BadRequestAlertException("Submission cannot be null", "exampleSubmission", "exampleSubmission.submissionIsNull");
        }
        Long submissionId = exampleSubmission.getSubmission().getId();
        if (submissionId == null) {
            throw new BadRequestAlertException("Submission ID must be persisted before conversion", "exampleSubmission", "exampleSubmission.submissionIdMissing");
        }
        return new ExampleSubmissionDTO(exampleSubmissionId, exampleSubmission.isUsedForTutorial(), submissionId, exampleSubmission.getAssessmentExplanation());
    }
}
