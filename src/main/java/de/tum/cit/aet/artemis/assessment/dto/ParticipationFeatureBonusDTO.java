package de.tum.cit.aet.artemis.assessment.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The feature bonus of one participation in an exercise.
 *
 * @param participationId the id of the participation
 * @param points          the sum of the points awarded to the participation
 * @param bonusPercent    the points converted with the settings of the course and capped at its maximum, or null while the course
 *                            has no conversion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationFeatureBonusDTO(long participationId, double points, @Nullable Double bonusPercent) {
}
