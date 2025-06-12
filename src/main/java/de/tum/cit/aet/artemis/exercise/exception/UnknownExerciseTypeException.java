package de.tum.cit.aet.artemis.exercise.exception;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

public class UnknownExerciseTypeException extends EntityNotFoundException {

    public UnknownExerciseTypeException(Exercise exercise) {
        super("Artemis doesn't recognize \"" + (exercise != null ? exercise.getType() : "unknown") + "\" as a valid exercise type");
    }
}
