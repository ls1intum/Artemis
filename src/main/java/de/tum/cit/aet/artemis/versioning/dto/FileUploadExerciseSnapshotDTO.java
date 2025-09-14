package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadExerciseSnapshotDTO(String exampleSolution, String filePattern) implements Serializable {

    public static FileUploadExerciseSnapshotDTO of(FileUploadExercise exercise) {
        return new FileUploadExerciseSnapshotDTO(exercise.getExampleSolution(), exercise.getFilePattern());
    }
}
