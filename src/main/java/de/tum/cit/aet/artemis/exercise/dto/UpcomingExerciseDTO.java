package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * One row of the admin "upcoming exams and exercises" table: the exercise icon and title, the course it belongs to and
 * the two dates the table shows. The table links to the exercise and to its course, which is why both ids are reported.
 * <p>
 * The query behind it only selects course exercises, so the course is always the exercise's own course.
 *
 * @param id          the id of the exercise
 * @param type        the exercise discriminator the table picks its icon from
 * @param title       the title of the exercise
 * @param releaseDate the release date shown in the table
 * @param dueDate     the due date shown in the table, which the rows are ordered by
 * @param course      the course the exercise belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpcomingExerciseDTO(long id, String type, @Nullable String title, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime dueDate,
        @Nullable UpcomingExerciseCourseDTO course) {

    /**
     * The course of an upcoming exercise, reduced to what the table links to and renders.
     *
     * @param id    the id of the course
     * @param title the title of the course
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record UpcomingExerciseCourseDTO(long id, @Nullable String title) {
    }

    /**
     * Maps an upcoming course exercise and its eagerly loaded course.
     *
     * @param exercise the exercise to map
     * @return the table row
     */
    public static UpcomingExerciseDTO of(Exercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        UpcomingExerciseCourseDTO courseDTO = course == null ? null : new UpcomingExerciseCourseDTO(course.getId(), course.getTitle());
        return new UpcomingExerciseDTO(exercise.getId(), exercise.getType(), exercise.getTitle(), exercise.getReleaseDate(), exercise.getDueDate(), courseDTO);
    }
}
