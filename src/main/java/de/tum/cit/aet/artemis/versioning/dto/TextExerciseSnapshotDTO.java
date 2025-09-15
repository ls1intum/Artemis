package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.text.domain.TextExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseSnapshotDTO(String exampleSolution) implements Serializable {

    public static TextExerciseSnapshotDTO of(TextExercise exercise) {
        return new TextExerciseSnapshotDTO(exercise.getExampleSolution());
    }
}
