package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExampleSubmissionDTO(long id, boolean usedForTutorial, String assessmentExplanation, long submissionId, long exerciseId) {

    public static ExampleSubmissionDTO of(ExampleSubmission es) {
        return new ExampleSubmissionDTO(es.getId(), es.isUsedForTutorial(), es.getAssessmentExplanation(), es.getSubmission().getId(), es.getExercise().getId());
    }
}
