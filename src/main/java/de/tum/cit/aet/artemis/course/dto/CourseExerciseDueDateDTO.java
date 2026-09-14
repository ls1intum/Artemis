package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/** Exercise fields used by the lecture PDF date-box. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseExerciseDueDateDTO(long id, ExerciseType type, String title, ZonedDateTime dueDate, @Nullable Set<String> categories) {

    /** Maps the due-date repository projection. */
    public static CourseExerciseDueDateDTO of(Exercise exercise) {
        return new CourseExerciseDueDateDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getDueDate(), Set.copyOf(exercise.getCategories()));
    }
}
