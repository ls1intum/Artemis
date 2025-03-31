package de.tum.cit.aet.artemis.quiz.dto.result;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultBeforeEvaluationDTO(Long id, ZonedDateTime completionDate, Boolean rated) {

    /**
     * Creates a ResultBeforeEvaluationDTO object from a Result object.
     *
     * @param result the Result object
     * @return the created ResultBeforeEvaluationDTO object
     */
    public static ResultBeforeEvaluationDTO of(Result result) {
        return new ResultBeforeEvaluationDTO(result.getId(), result.getCompletionDate(), result.isRated());
    }

}
