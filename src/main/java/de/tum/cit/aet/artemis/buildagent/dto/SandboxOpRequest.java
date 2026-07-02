package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * A single interactive-sandbox operation that a core node asks a specific remote build agent to perform on the warm container it owns.
 * <p>
 * Requests are broadcast over the {@code hyperion-sandbox-requests} {@link de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic} (because build agents
 * commonly run as Hazelcast clients, so a member-targeted RPC is not available) and self-filtered on the build agent by {@link #targetAgentShortName}: only the agent whose short
 * name matches handles the request; every other agent ignores it. The {@link #correlationId} ties the eventual {@link SandboxOpResponse} back to the blocked caller and makes
 * handling idempotent if the broadcast is delivered more than once.
 *
 * @param correlationId        unique id correlating this request with its {@link SandboxOpResponse}; also the idempotency key on the handler
 * @param targetAgentShortName the short name of the build agent that owns the session and must handle this request (all other agents ignore it)
 * @param op                   the operation to perform
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
}
