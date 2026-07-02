package de.tum.cit.aet.artemis.hyperion.exercisegeneration.persistence;

/**
 * Thrown when persisting a generated exercise fails part-way through the multi-repository commit sequence. The three repositories (template, solution, tests) cannot be committed
 * inside a single database/git transaction, so {@link GenerationPersistenceService} compensates by reverting the repositories it had already committed back to their pre-generation
 * state before raising this exception. It signals the caller that the generation is INCOMPLETE and the exercise must NOT be treated as a publishable, generated result — either the
 * repositories were reverted to their previous consistent version, or (if compensation itself failed) the exercise is in an inconsistent state that needs manual review.
 */
public class GenerationIncompleteException extends RuntimeException {

    public GenerationIncompleteException(String message, Throwable cause) {
        super(message, cause);
    }
}
