package de.tum.cit.aet.artemis.metis.service;

import org.jspecify.annotations.Nullable;

/**
 * A Metis LLM call that produced no usable answer, classified by what the caller should do about it.
 */
public class MetisLlmException extends RuntimeException {

    /**
     * Why the call produced no usable answer.
     */
    public enum Kind {
        /**
         * The endpoint is not configured, not reachable, overloaded or misconfigured, or no permit became free in time. A
         * later attempt may succeed.
         */
        UNAVAILABLE,
        /** The endpoint refused this request (for example a context that is too long); the same request will never succeed. */
        REJECTED,
        /** The endpoint answered, but the answer is empty or cannot be used. */
        INVALID_OUTPUT
    }

    private final Kind kind;

    /**
     * Creates the exception without an underlying cause.
     *
     * @param kind    why the call produced no usable answer
     * @param message what happened, without any prompt content
     */
    public MetisLlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * Creates the exception for an underlying failure.
     *
     * @param kind    why the call produced no usable answer
     * @param message what happened, without any prompt content
     * @param cause   the failure of the call
     */
    public MetisLlmException(Kind kind, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * Why the call produced no usable answer.
     *
     * @return the kind of failure
     */
    public Kind getKind() {
        return kind;
    }
}
