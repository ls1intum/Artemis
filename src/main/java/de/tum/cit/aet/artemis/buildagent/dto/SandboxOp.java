package de.tum.cit.aet.artemis.buildagent.dto;

/**
 * The interactive-sandbox operation a {@link SandboxOpRequest} asks a remote build agent to perform. Each value maps one-to-one to a method of
 * {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox}, so the relay can dispatch a request to the local sandbox implementation without any further type
 * inspection.
 */
public enum SandboxOp {

    /** @see de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#createSession */
    CREATE,

    /** @see de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#exec */
    EXEC,

    /** @see de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#copyIn */
    COPY_IN,

    /** @see de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#copyOut */
    COPY_OUT,

    /** Lists live generation sandbox sessions on the target agent. */
    LIST,

    /** @see de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#destroySession */
    DESTROY
}
