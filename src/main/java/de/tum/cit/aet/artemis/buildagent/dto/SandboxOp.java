package de.tum.cit.aet.artemis.buildagent.dto;

/**
 * The interactive-sandbox operation a {@link SandboxOpRequestDTO} asks a remote build agent to perform. Each value maps one-to-one to a method of
 * {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox}, so the relay can dispatch a request to the local sandbox implementation without any further type
 * inspection.
 */
public enum SandboxOp {

    CREATE, EXEC, COPY_IN, COPY_OUT, RESET,

    /** The one operation with no {@code InteractiveSandbox} counterpart: lists the live generation sandbox sessions on the target agent. */
    LIST,

    DESTROY
}
