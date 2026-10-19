package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Everything the course score calculation reads off the grading scale: the graded presentation configuration.
 * <p>
 * A {@code null} config means the course does not award points for presentations, which is the case for a course without
 * a grading scale, for a grading scale that belongs to an exam rather than a course, and for a grading scale that simply
 * has no presentations configured. All three used to be distinguished deep inside the calculation; collapsing them here
 * is why the calculation itself only has to check for {@code null}.
 *
 * @param presentationsNumber how many presentations the average is taken over; {@code 0} disables graded presentations
 * @param presentationsWeight the share of the course's total points, in percent, that presentations account for;
 *                                {@code 0} disables graded presentations
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradedPresentationConfigDTO(int presentationsNumber, double presentationsWeight) {
}
