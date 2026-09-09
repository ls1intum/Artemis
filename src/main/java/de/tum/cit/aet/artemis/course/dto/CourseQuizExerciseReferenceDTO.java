package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Minimal quiz-exercise reference used when selecting a source course.
 *
 * @param id        the quiz exercise identifier
 * @param title     the quiz exercise title
 * @param shortName the optional exercise short name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseQuizExerciseReferenceDTO(long id, String title, @Nullable String shortName) {

    /**
     * Maps a quiz exercise without questions, batches, grading data, or back-references.
     *
     * @param exercise the quiz exercise to map
     * @return the quiz exercise reference
     */
    public static CourseQuizExerciseReferenceDTO of(QuizExercise exercise) {
        return new CourseQuizExerciseReferenceDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName());
    }
}
