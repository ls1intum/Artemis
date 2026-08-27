package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Minimal course context required by participation clients.
 *
 * @param id               the unique identifier of the course
 * @param title            the course title, if available
 * @param shortName        the course short name, if available
 * @param accuracyOfScores the configured number of decimal places for scores, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationCourseContextDTO(Long id, @Nullable String title, @Nullable String shortName, @Nullable Integer accuracyOfScores) {

    /**
     * Creates a minimal course context response.
     *
     * @param course the course to map
     * @return the minimal course context
     */
    public static ParticipationCourseContextDTO of(Course course) {
        return new ParticipationCourseContextDTO(course.getId(), course.getTitle(), course.getShortName(), course.getAccuracyOfScores());
    }
}
