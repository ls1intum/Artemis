package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * A single interactive-sandbox operation a core node asks a specific remote build agent to perform on the warm container it owns. Requests are broadcast over the
 * {@code hyperion-sandbox-requests} {@link de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic} (build agents commonly run as Hazelcast clients, so a
 * member-targeted RPC is not available) and self-filtered by {@link #targetAgentShortName}; the {@link #correlationId} ties the eventual {@link SandboxOpResponse} back to the
 * blocked caller and makes handling idempotent under redelivery.
 *
 * @param correlationId        unique id correlating this request with its {@link SandboxOpResponse}; also the idempotency key on the handler
 * @param targetAgentShortName the short name of the build agent that owns the session and must handle this request (all other agents ignore it)
 * @param sessionId            the container id of the session for non-create operations; {@code null} for {@link SandboxOp#CREATE}
 * @param sessionSpec          the session specification for {@link SandboxOp#CREATE}; {@code null} otherwise
 * @param command              the command and its arguments for {@link SandboxOp#EXEC}; {@code null} otherwise
 * @param timeoutSeconds       the per-operation timeout in seconds, applied to the exec inside the container (and used to derive the relay wait budget on the caller)
 * @param payload              the tar bytes for {@link SandboxOp#COPY_IN}; {@code null} otherwise
 * @param workspacePath        the absolute container path for {@link SandboxOp#COPY_IN} (destination) and {@link SandboxOp#COPY_OUT} (source); {@code null} otherwise
 */
public record SandboxOpRequest(String correlationId, String targetAgentShortName, SandboxOp op, String sessionId, SandboxSessionSpec sessionSpec, String[] command,
        long timeoutSeconds, byte[] payload, String workspacePath) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A {@link SandboxOp#CREATE} request: only the session spec is carried. */
    public static SandboxOpRequest create(String correlationId, String targetAgentShortName, SandboxSessionSpec sessionSpec) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.CREATE, null, sessionSpec, null, 0L, null, null);
    }

    /** An {@link SandboxOp#EXEC} request against an existing session, with the command and its per-op timeout. */
    public static SandboxOpRequest exec(String correlationId, String targetAgentShortName, String sessionId, String[] command, long timeoutSeconds) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.EXEC, sessionId, null, command, timeoutSeconds, null, null);
    }

    /** A {@link SandboxOp#COPY_IN} request writing the tar {@code payload} to {@code workspacePath} inside the session. */
    public static SandboxOpRequest copyIn(String correlationId, String targetAgentShortName, String sessionId, byte[] payload, String workspacePath) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.COPY_IN, sessionId, null, null, 0L, payload, workspacePath);
    }

    /** A {@link SandboxOp#COPY_OUT} request reading {@code workspacePath} out of the session as a tar archive. */
    public static SandboxOpRequest copyOut(String correlationId, String targetAgentShortName, String sessionId, String workspacePath) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.COPY_OUT, sessionId, null, null, 0L, null, workspacePath);
    }

    /** A {@link SandboxOp#DESTROY} request tearing down an existing session. */
    public static SandboxOpRequest destroy(String correlationId, String targetAgentShortName, String sessionId) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.DESTROY, sessionId, null, null, 0L, null, null);
    }
}
