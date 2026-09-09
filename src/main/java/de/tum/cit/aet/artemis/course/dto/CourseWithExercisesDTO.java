package de.tum.cit.aet.artemis.course.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Course management data together with its exercises.
 *
 * @param course    the course settings
 * @param exercises the exercises visible to the requesting tutor
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseWithExercisesDTO(@JsonUnwrapped CourseManagementDTO course, Set<CourseManagementExerciseDTO> exercises) {

    /** Maps the already authorized and fetched course graph. */
    public static CourseWithExercisesDTO of(Course course) {
        return new CourseWithExercisesDTO(CourseManagementDTO.of(course), course.getExercises().stream().map(CourseManagementExerciseDTO::of).collect(Collectors.toSet()));
    }
}
