package de.tum.cit.aet.artemis.assessment.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The feature bonus of one student in a course, over all exercises and including the awards of the student's teams.
 *
 * @param studentId    the id of the student
 * @param points       the sum of the points awarded to the student and the student's teams
 * @param bonusPercent the points converted with the settings of the course and capped at its maximum, or null while the course has
 *                         no conversion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentFeatureBonusDTO(long studentId, double points, @Nullable Double bonusPercent) {
}
