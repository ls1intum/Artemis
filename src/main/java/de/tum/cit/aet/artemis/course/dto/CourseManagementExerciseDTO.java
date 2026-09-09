package de.tum.cit.aet.artemis.course.dto;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;

/**
 * DTO-safe union of the five exercise response contracts used by course-management content endpoints.
 */
public interface CourseManagementExerciseDTO {

    /**
     * Maps an exercise to its module-owned response DTO.
     *
     * @param exercise the exercise to map
     * @return the corresponding response DTO
     */
    static CourseManagementExerciseDTO of(Exercise exercise) {
        return switch (exercise) {
            case ProgrammingExercise programmingExercise -> ProgrammingExerciseResponseDTO.of(programmingExercise);
            case TextExercise textExercise -> TextExerciseResponseDTO.of(textExercise);
            case ModelingExercise modelingExercise -> ModelingExerciseResponseDTO.of(modelingExercise);
            case FileUploadExercise fileUploadExercise -> FileUploadExerciseDTO.forCourseList(fileUploadExercise);
            case QuizExercise quizExercise -> QuizExerciseWithoutQuestionsDTO.of(quizExercise);
            default -> throw new IllegalArgumentException("Unsupported exercise type: " + exercise.getClass().getName());
        };
    }
}
