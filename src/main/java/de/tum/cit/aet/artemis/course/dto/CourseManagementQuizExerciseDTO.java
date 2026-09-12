package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupReferenceDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;

/**
 * Course-management adapter for the quiz exercise response. The adapter keeps the existing flat JSON shape without making the shared quiz DTO polymorphic, as that DTO is also
 * embedded with {@link JsonUnwrapped} in quiz-module responses.
 */
public record CourseManagementQuizExerciseDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise) implements CourseManagementExerciseDTO {

    public static CourseManagementQuizExerciseDTO of(QuizExercise exercise) {
        return new CourseManagementQuizExerciseDTO(QuizExerciseWithoutQuestionsDTO.of(exercise));
    }

    @Override
    public Long id() {
        return quizExercise.id();
    }

    @Override
    public String title() {
        return quizExercise.title();
    }

    @Override
    public String type() {
        return quizExercise.type();
    }

    @Override
    @Nullable
    public ExerciseVariantGroupReferenceDTO exerciseVariantGroup() {
        return quizExercise.exerciseVariantGroup();
    }
}
