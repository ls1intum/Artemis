package de.tum.cit.aet.artemis.quiz.dto.result;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultAfterEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, AssessmentType assessmentType) {

    /**
     * Creates a ResultAfterEvaluationDTO object from a Result object.
     *
     * @param result the Result object
     * @return the created ResultAfterEvaluationDTO object
     */
    public static ResultAfterEvaluationDTO of(Result result) {
        return new ResultAfterEvaluationDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), result.getAssessmentType());
    }

}
