package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stable exception representation for distributed build result queues.
 * It preserves the original exception type name and message without depending
 * on reflective construction of arbitrary JDK or third-party exception types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class BuildResultQueueException extends RuntimeException implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String originalClassName;

    @JsonCreator
    public BuildResultQueueException(@JsonProperty("originalClassName") String originalClassName, @JsonProperty("message") String message,
            @JsonProperty("cause") BuildResultQueueException cause) {
        super(message, cause, true, false);
        this.originalClassName = originalClassName;
    }

    public static BuildResultQueueException from(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof BuildResultQueueException buildResultQueueException) {
            return buildResultQueueException;
        }
        return new BuildResultQueueException(throwable.getClass().getName(), throwable.getMessage(), from(throwable.getCause()));
    }

    public String getOriginalClassName() {
        return originalClassName;
    }

    @Override
    public String toString() {
        String message = getMessage();
        if (message == null || message.isBlank()) {
            return originalClassName;
        }
        return originalClassName + ": " + message;
    }
}
