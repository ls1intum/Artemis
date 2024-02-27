package de.tum.in.www1.artemis.web.rest.dto.score;

/**
 * Represents the sum of points achieved by a team.
 *
 * @param teamId            the id of the team
 * @param sumPointsAchieved the sum of points achieved by the team (can be null, e.g., if the team did not participate in any exercise in the course)
 */
public record TeamScoreSum(long teamId, Double sumPointsAchieved) {
}
