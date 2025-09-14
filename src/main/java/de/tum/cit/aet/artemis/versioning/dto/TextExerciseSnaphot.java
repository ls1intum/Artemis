package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;

import de.tum.cit.aet.artemis.text.domain.TextExercise;

public record TextExerciseSnaphot(String exampleSolution) implements Serializable {

    public static TextExerciseSnaphot of(TextExercise exercise) {
        return new TextExerciseSnaphot(exercise.getExampleSolution());
    }
}
