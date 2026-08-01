package de.tum.cit.aet.artemis.quiz.dto.exercise;

/**
 * Marker for the response shapes returned by the student quiz exercise endpoint. The variants deliberately add no Jackson type information so that the existing JSON wire
 * format remains unchanged.
 */
public sealed interface QuizExerciseForStudentResponseDTO permits QuizExerciseWithoutQuestionsDTO, QuizExerciseWithQuestionsDTO, QuizExerciseWithSolutionDTO {
}
