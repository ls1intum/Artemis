package de.tum.cit.aet.artemis.assessment.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the sum of points achieved by a student.
 *
 * @param userId            the id of the student
 * @param sumPointsAchieved the sum of points achieved by the student
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentScoreSum(long userId, double sumPointsAchieved) {
}
