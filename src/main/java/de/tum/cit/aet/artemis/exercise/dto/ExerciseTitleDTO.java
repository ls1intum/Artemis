package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * The minimum needed to list an exercise for selection: its id, its title and its type.
 * <p>
 * Used where a caller only has to name exercises, not show them — the Iris chat context picker being the first such
 * caller. It previously loaded the whole course exercise payload, participations, submissions, results and scores
 * included, to fill a dropdown with these three fields.
 *
 * @param id    the id of the exercise
 * @param title the title of the exercise
 * @param type  the type of the exercise, which decides the icon and the chat mode
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTitleDTO(long id, String title, ExerciseType type) {

    /**
     * JPQL constructor accepting the entity class produced by Hibernate's {@code TYPE(...)} function.
     */
    public ExerciseTitleDTO(long id, String title, Class<? extends Exercise> type) {
        this(id, title, ExerciseType.getExerciseTypeFromClass(type));
    }
}
