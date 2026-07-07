package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * The reply a remote build agent publishes after performing a {@link SandboxOpRequest}. Broadcast over the {@code hyperion-sandbox-responses}
 * {@link de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic} and matched back to the blocked caller by {@link #correlationId}; on failure
 * {@link #success} is {@code false} and {@link #errorMessage} carries a short description the caller rethrows as a session-fatal exception.
 *
 * @param correlationId the id of the {@link SandboxOpRequest} this response answers
 * @param sessionId     the created container id for {@link SandboxOp#CREATE}; echoed back otherwise (may be {@code null})
 * @param execResult    the captured exit code and bounded stdout/stderr for {@link SandboxOp#EXEC}; {@code null} otherwise
 * @param errorMessage  a short error description when {@link #success} is {@code false}; {@code null} on success
 */
public record SandboxOpResponse(String correlationId, boolean success, String sessionId, SandboxExecResult execResult, String errorMessage) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A success response carrying no further data (used by {@link SandboxOp#COPY_IN} and {@link SandboxOp#DESTROY}). */
    public static SandboxOpResponse ok(String correlationId, String sessionId) {
        return new SandboxOpResponse(correlationId, true, sessionId, null, null);
    }

    /** A {@link SandboxOp#CREATE} success response carrying the new container id as the session handle. */
    public static SandboxOpResponse created(String correlationId, String containerId) {
        return new SandboxOpResponse(correlationId, true, containerId, null, null);
    }

    /** An {@link SandboxOp#EXEC} success response carrying the captured exit code and bounded output. */
    public static SandboxOpResponse exec(String correlationId, String sessionId, SandboxExecResult execResult) {
        return new SandboxOpResponse(correlationId, true, sessionId, execResult, null);
    }

    /**
     * A {@link SandboxOp#COPY_OUT} success response. The repacked tar bytes ride the keyed staging map (keyed by {@code correlationId}), not the response itself, so only the
     * originating core node fetches them.
     */
    public static SandboxOpResponse copiedOut(String correlationId, String sessionId) {
        return new SandboxOpResponse(correlationId, true, sessionId, null, null);
    }

    /** A failure response carrying a short error description for the blocked caller to rethrow. */
    public static SandboxOpResponse failure(String correlationId, String errorMessage) {
        return new SandboxOpResponse(correlationId, false, null, null, errorMessage);
    }
}
