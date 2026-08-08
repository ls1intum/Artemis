package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * A result as the course overview renders it: enough to show the score badge on an exercise card.
 * <p>
 * Feedbacks are deliberately absent. They are only read by the exercise scores export and the assessment views, which
 * load results of their own; carrying them here would dominate the payload for nothing.
 *
 * @param id             the id of the result
 * @param completionDate when the result was produced, used to pick the latest one
 * @param score          the achieved score in percent
 * @param rated          whether the result counts towards the score
 * @param successful     whether the result is considered successful
 * @param assessmentType how the result was produced, which decides the badge style
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultOverviewDTO(Long id, ZonedDateTime completionDate, Double score, Boolean rated, Boolean successful, AssessmentType assessmentType) {

    /**
     * Projects a result for the course overview.
     *
     * @param result the result to project
     * @return the projected result
     */
    public static ResultOverviewDTO of(Result result) {
        return new ResultOverviewDTO(result.getId(), result.getCompletionDate(), result.getScore(), result.isRated(), result.isSuccessful(), result.getAssessmentType());
    }
}
