package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The two names a message to a user needs to say which exercise a git operation touched, without loading the exercise or its course.
 *
 * @param exerciseTitle the title of the exercise
 * @param courseTitle   the title of the course the exercise belongs to, directly or through its exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseAndCourseTitlesDTO(String exerciseTitle, @Nullable String courseTitle) {
}
