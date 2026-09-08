package de.tum.cit.aet.artemis.hyperion.runtime.agent;

/** Signals loss of the execution environment, rather than a model-correctable tool invocation error. */
public class SandboxUnavailableException extends RuntimeException {

    public SandboxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
