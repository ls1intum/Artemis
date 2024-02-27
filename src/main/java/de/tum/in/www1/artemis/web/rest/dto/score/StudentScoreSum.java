package de.tum.in.www1.artemis.web.rest.dto.score;

/**
 * Represents the sum of points achieved by a student.
 *
 * @param userId            the id of the student
 * @param sumPointsAchieved the sum of points achieved by the student (can be null, e.g., if the student did not participate in any exercise in the course)
 */
public record StudentScoreSum(long userId, Double sumPointsAchieved) {
}
