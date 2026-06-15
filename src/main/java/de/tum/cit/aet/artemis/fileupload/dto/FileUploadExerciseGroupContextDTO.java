package de.tum.cit.aet.artemis.fileupload.dto;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * DTO representing the exercise group context for a file upload exercise in an exam.
 *
 * @param id   the ID of the exercise group
 * @param exam the exam context of the exercise group
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseGroupContextDTO(Long id, FileUploadExamContextDTO exam) {

    /**
     * Factory method to create a {@link FileUploadExerciseGroupContextDTO} from an {@link ExerciseGroup} entity.
     *
     * @param exerciseGroup the exercise group entity to map, can be null
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadExerciseGroupContextDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null) {
            return null;
        }
        Exam exam = exerciseGroup.getExam();
        FileUploadExamContextDTO examDTO = exam != null && Hibernate.isInitialized(exam) ? FileUploadExamContextDTO.of(exam) : null;
        return new FileUploadExerciseGroupContextDTO(exerciseGroup.getId(), examDTO);
    }
}
