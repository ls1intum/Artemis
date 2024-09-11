package de.tum.cit.aet.artemis.web.rest.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the sum of points achieved by a team.
 *
 * @param teamId            the id of the team
 * @param sumPointsAchieved the sum of points achieved by the team
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamScoreSum(long teamId, double sumPointsAchieved) {
}
