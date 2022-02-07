package de.tum.in.www1.artemis.domain.assessment.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Class used to hold tutor average rating and number of tutor ratings in an exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseRatingCount(Double averageRating, Long numberOfRatings) {
}
