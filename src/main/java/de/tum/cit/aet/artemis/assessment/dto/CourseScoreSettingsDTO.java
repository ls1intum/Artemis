package de.tum.cit.aet.artemis.assessment.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Everything the course score calculation reads off the course: two numbers.
 * <p>
 * Passing these instead of the {@code Course} entity is what lets the calculation be a pure function. The course is a
 * large entity with lazy collections, so taking it as a parameter both hides how little is actually used and makes the
 * calculation depend on the entity's load state.
 *
 * @param accuracyOfScores  the number of decimal places every intermediate and final score is rounded to. Rounding
 *                              happens per exercise before summing, so that a student adding up the displayed points
 *                              arrives at the displayed total.
 * @param presentationScore the number of presentations a student must hold for the <em>basic</em> presentation scheme,
 *                              or {@code null} when the course does not use it. Unrelated to the graded presentations
 *                              configured on the grading scale (see {@link GradedPresentationConfigDTO}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoreSettingsDTO(int accuracyOfScores, @Nullable Integer presentationScore) {

    /**
     * Reads the score-relevant settings off a course entity.
     *
     * @param course the course whose settings apply to the calculation
     * @return the settings of the given course
     */
    public static CourseScoreSettingsDTO from(Course course) {
        return new CourseScoreSettingsDTO(course.getAccuracyOfScores(), course.getPresentationScore());
    }
}
