package de.tum.cit.aet.artemis.deimos.exception;

import java.io.Serial;

import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;

/**
 * Thrown when the Deimos LLM call fails or its response cannot be turned into a verdict.
 * <p>
 * Carries the {@link DeimosFailureType} so the batch summary and the completion email can tell an instructor whether
 * the model was unreachable, rate limited, or simply answered with something unparseable.
 */
public class DeimosLlmException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DeimosFailureType failureType;

    public DeimosLlmException(DeimosFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public DeimosLlmException(DeimosFailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public DeimosFailureType getFailureType() {
        return failureType;
    }
}
