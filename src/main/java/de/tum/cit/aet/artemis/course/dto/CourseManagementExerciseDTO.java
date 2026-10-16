package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupReferenceDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;

/**
 * DTO-safe union of the five exercise response contracts used by course-management content endpoints.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = ProgrammingExerciseResponseDTO.class, name = "programming"), @JsonSubTypes.Type(value = TextExerciseResponseDTO.class, name = "text"),
        @JsonSubTypes.Type(value = ModelingExerciseResponseDTO.class, name = "modeling"), @JsonSubTypes.Type(value = FileUploadExerciseDTO.class, name = "file-upload"),
        @JsonSubTypes.Type(value = CourseManagementQuizExerciseDTO.class, name = "quiz") })
public interface CourseManagementExerciseDTO {

    Long id();

    String title();

    String type();

    @Nullable
    ExerciseVariantGroupReferenceDTO exerciseVariantGroup();

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
            case QuizExercise quizExercise -> CourseManagementQuizExerciseDTO.of(quizExercise);
            default -> throw new IllegalArgumentException("Unsupported exercise type: " + exercise.getClass().getName());
        };
    }
}
