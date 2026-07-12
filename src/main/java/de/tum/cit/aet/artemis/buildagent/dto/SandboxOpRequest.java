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
 * @param createPermits        the number of generation sandbox slots this {@link SandboxOp#CREATE} reserves; ignored for other operations
 * @param command              the command and its arguments for {@link SandboxOp#EXEC}; {@code null} otherwise
 * @param timeoutSeconds       the per-operation timeout in seconds, applied to the exec inside the container (and used to derive the relay wait budget on the caller)
 * @param workspacePath        the absolute container path for {@link SandboxOp#COPY_IN} (destination) and {@link SandboxOp#COPY_OUT} (source); {@code null} otherwise
 */
public record SandboxOpRequest(String correlationId, String targetAgentShortName, SandboxOp op, String sessionId, SandboxSessionSpec sessionSpec, String[] command,
        int createPermits, long timeoutSeconds, String workspacePath) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A {@link SandboxOp#CREATE} request: only the session spec is carried. */
    public static SandboxOpRequest create(String correlationId, String targetAgentShortName, SandboxSessionSpec sessionSpec) {
        return create(correlationId, targetAgentShortName, sessionSpec, 1);
    }

    /** A {@link SandboxOp#CREATE} request reserving the given number of generation sandbox slots. */
    public static SandboxOpRequest create(String correlationId, String targetAgentShortName, SandboxSessionSpec sessionSpec, int createPermits) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.CREATE, null, sessionSpec, null, createPermits, 0L, null);
    }

    /** A {@link SandboxOp#CREATE} request that consumes a slot already reserved by the given authoring sandbox. */
    public static SandboxOpRequest createVerification(String correlationId, String targetAgentShortName, SandboxSessionSpec sessionSpec, String authoringSessionId) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.CREATE, authoringSessionId, sessionSpec, null, 0, 0L, null);
    }

    /** An {@link SandboxOp#EXEC} request against an existing session, with the command and its per-op timeout. */
    public static SandboxOpRequest exec(String correlationId, String targetAgentShortName, String sessionId, String[] command, long timeoutSeconds) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.EXEC, sessionId, null, command, 0, timeoutSeconds, null);
    }

    /**
     * A {@link SandboxOp#COPY_IN} request writing to {@code workspacePath} inside the session. The tar bytes ride the keyed staging map (keyed by {@code correlationId}), not the
     * request itself, so only the target agent transfers them.
     */
    public static SandboxOpRequest copyIn(String correlationId, String targetAgentShortName, String sessionId, String workspacePath) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.COPY_IN, sessionId, null, null, 0, 0L, workspacePath);
    }

    /** A {@link SandboxOp#COPY_OUT} request reading {@code workspacePath} out of the session as a tar archive. */
    public static SandboxOpRequest copyOut(String correlationId, String targetAgentShortName, String sessionId, String workspacePath) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.COPY_OUT, sessionId, null, null, 0, 0L, workspacePath);
    }

    /** A {@link SandboxOp#DESTROY} request tearing down an existing session. */
    public static SandboxOpRequest destroy(String correlationId, String targetAgentShortName, String sessionId) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.DESTROY, sessionId, null, null, 0, 0L, null);
    }

    public static SandboxOpRequest list(String correlationId, String targetAgentShortName) {
        return new SandboxOpRequest(correlationId, targetAgentShortName, SandboxOp.LIST, null, null, null, 0, 0L, null);
    }
}
