package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * File-upload-exercise-specific fields carried in the conduction payload (unwrapped into the exercise object). The
 * example solution is already stripped from the entity before this factory runs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseForConductionDTO(String filePattern) {

    /**
     * Extracts the file-upload-specific fields.
     *
     * @param fileUploadExercise the file upload exercise to convert
     * @return the file-upload-specific fields
     */
    public static FileUploadExerciseForConductionDTO of(FileUploadExercise fileUploadExercise) {
        return new FileUploadExerciseForConductionDTO(fileUploadExercise.getFilePattern());
    }
}
