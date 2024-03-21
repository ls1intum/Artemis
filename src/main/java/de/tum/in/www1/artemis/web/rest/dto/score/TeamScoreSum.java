package de.tum.in.www1.artemis.web.rest.dto.score;

/**
 * Represents the sum of points achieved by a team.
 *
 * @param teamId            the id of the team
 * @param sumPointsAchieved the sum of points achieved by the team
 */
public record TeamScoreSum(long teamId, double sumPointsAchieved) {
}
