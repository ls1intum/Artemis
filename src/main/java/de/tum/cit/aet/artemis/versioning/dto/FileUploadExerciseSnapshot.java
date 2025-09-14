package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;

import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

public record FileUploadExerciseSnapshot(String exampleSolution, String filePattern) implements Serializable {

    public static FileUploadExerciseSnapshot of(FileUploadExercise exercise) {
        return new FileUploadExerciseSnapshot(exercise.getExampleSolution(), exercise.getFilePattern());
    }
}
