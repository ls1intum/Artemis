package de.tum.in.www1.artemis.web.rest.dto.score;

/**
 * Represents the sum of points achieved by a student.
 *
 * @param userId            the id of the student
 * @param sumPointsAchieved the sum of points achieved by the student
 */
public record StudentScoreSum(long userId, double sumPointsAchieved) {
}
