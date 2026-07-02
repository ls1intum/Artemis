package de.tum.cit.aet.artemis.buildagent.dto;

/**
 * The interactive-sandbox operation a {@link SandboxOpRequest} asks a remote build agent to perform. Each value maps one-to-one to a method of
 * {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox}, so the relay can dispatch a request to the local sandbox implementation without any further type
 * inspection.
 */
public enum SandboxOp {

    /** Create and start a warm session container; see {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#createSession}. */
    CREATE,

    /** Run a command inside the session container; see {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#exec}. */
    EXEC,

    /** Copy a tar archive into the session container; see {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#copyIn}. */
    COPY_IN,

    /** Read a path out of the session container as a tar archive; see {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#copyOut}. */
    COPY_OUT,

    /** Stop and remove the session container; see {@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox#destroySession}. */
    DESTROY
}
