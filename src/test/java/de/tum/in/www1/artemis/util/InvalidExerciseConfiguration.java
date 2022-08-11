package de.tum.in.www1.artemis.util;

import org.springframework.http.HttpStatus;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Represents an invalid exercise configuration that can be {@linkplain #applyTo(Exercise) applied} to any given exercise of type <code>E</code>.
 * <p>
 * A configuration is invalid if it results in a {@link HttpStatus#BAD_REQUEST} when any exercise of the right type that the configuration has been applied to is sent to the server, for example via a create or update request.
 * @param <E> specifies the part of the {@link Exercise} hierarchy a configuration can be applied to
 * @implNote this might be useful for future invalidity checks that need to be executed for many different exercise types
 */
public interface InvalidExerciseConfiguration<E extends Exercise> {

    /**
     * Applies the invalid configuration to the given exercise object and returns it.
     * @param <T> the exercise subclass
     * @param exercise the exercise that should be modified
     * @return the same exercise object for convenience
     */
    <T extends E> T applyTo(T exercise);
}
