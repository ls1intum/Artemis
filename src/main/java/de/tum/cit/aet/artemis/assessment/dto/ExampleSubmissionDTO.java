package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionDTO(long id, boolean usedForTutorial, long submissionId, String assessmentExplanation) {

    /**
     * Convert an ExampleSubmission entity to a ExampleSubmissionDTO.
     *
     * @param exampleSubmission the ExampleSubmission to convert
     */
    public static ExampleSubmissionDTO of(ExampleSubmission exampleSubmission) {
        return new ExampleSubmissionDTO(exampleSubmission.getId(), exampleSubmission.isUsedForTutorial(), exampleSubmission.getSubmission().getId(),
                exampleSubmission.getAssessmentExplanation());
    }
}
