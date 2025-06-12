package de.tum.cit.aet.artemis.exercise.exception;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

public class UnknownExerciseTypeException extends RuntimeException {

    public UnknownExerciseTypeException(Exercise exercise) {
        super("Artemis doesn't recognize \"" + exercise.getType() + "\" as a valid exercise type");
    }
}
