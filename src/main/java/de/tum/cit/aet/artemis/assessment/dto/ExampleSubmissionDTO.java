package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionDTO(long id, boolean usedForTutorial, long submissionId, @Nullable String assessmentExplanation) {

    /**
     * Convert an ExampleSubmission entity to a ExampleSubmissionDTO.
     *
     * @param exampleSubmission the ExampleSubmission to convert
     */
    public static ExampleSubmissionDTO of(ExampleSubmission exampleSubmission) {
        var submissionId = Objects.requireNonNull(exampleSubmission.getSubmission().getId(), "ExampleSubmission submission must already be persisted");
        return new ExampleSubmissionDTO(exampleSubmission.getId(), exampleSubmission.isUsedForTutorial(), submissionId, exampleSubmission.getAssessmentExplanation());
    }
}
