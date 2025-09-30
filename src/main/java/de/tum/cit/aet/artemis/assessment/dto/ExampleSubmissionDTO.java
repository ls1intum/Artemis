package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.annotation.Nullable;

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
    public static ExampleSubmissionDTO of(ExampleSubmission exampleSubmission) {
        if (exampleSubmission.getSubmission() == null || exampleSubmission.getSubmission().getId() == null) {
            throw new BadRequestAlertException("SubmissionId is missing", "exampleSubmission", "exampleSubmission.submissionIdMissing");
        }
        long submissionId = exampleSubmission.getSubmission().getId();
        return new ExampleSubmissionDTO(exampleSubmission.getId(), exampleSubmission.isUsedForTutorial(), submissionId, exampleSubmission.getAssessmentExplanation());
    }
}
