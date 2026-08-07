package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * A single interactive-sandbox operation a core node asks a specific remote build agent to perform on the warm container it owns. Requests are broadcast over the
 * {@code hyperion-sandbox-requests} {@link de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic} (build agents commonly run as Hazelcast clients, so a
 * member-targeted RPC is not available) and self-filtered by {@link #targetAgentShortName}; the {@link #correlationId} ties the eventual {@link SandboxOpResponseDTO} back to the
 * blocked caller. The handler retains a bounded terminal-response cache so retries with the same correlation id replay the result instead of repeating the side effect.
 *
 * @param correlationId        unique id correlating this request with its {@link SandboxOpResponseDTO}; also the idempotency key on the handler
 * @param targetAgentShortName the short name of the build agent that owns the session and must handle this request (all other agents ignore it)
 * @param sessionId            the container id of the session for non-create operations; {@code null} for {@link SandboxOp#CREATE}
 * @param sessionSpec          the session specification for {@link SandboxOp#CREATE}; {@code null} otherwise
 * @param command              the command and its arguments for {@link SandboxOp#EXEC}; {@code null} otherwise
 * @param timeoutSeconds       the per-operation timeout in seconds, applied to the exec inside the container (and used to derive the relay wait budget on the caller)
 * @param workspacePath        the absolute container path for {@link SandboxOp#COPY_IN} (destination) and {@link SandboxOp#COPY_OUT} (source); {@code null} otherwise
 * @param deadlineEpochMillis  wall-clock deadline after which a delayed request must not execute
 */
public record SandboxOpRequestDTO(String correlationId, String targetAgentShortName, SandboxOp op, String sessionId, SandboxSessionSpecDTO sessionSpec, String[] command,
        long timeoutSeconds, String workspacePath, long deadlineEpochMillis) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static SandboxOpRequestDTO create(String correlationId, String targetAgentShortName, SandboxSessionSpecDTO sessionSpec) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.CREATE, null, sessionSpec, null, 0L, null, 0L);
    }

    public static SandboxOpRequestDTO exec(String correlationId, String targetAgentShortName, String sessionId, String[] command, long timeoutSeconds) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.EXEC, sessionId, null, command, timeoutSeconds, null, 0L);
    }

    /**
     * A {@link SandboxOp#COPY_IN} request writing to {@code workspacePath} inside the session. The tar bytes ride the keyed staging map (keyed by {@code correlationId}), not the
     * request itself, so only the target agent transfers them.
     */
    public static SandboxOpRequestDTO copyIn(String correlationId, String targetAgentShortName, String sessionId, String workspacePath) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.COPY_IN, sessionId, null, null, 0L, workspacePath, 0L);
    }

    public static SandboxOpRequestDTO copyOut(String correlationId, String targetAgentShortName, String sessionId, String workspacePath) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.COPY_OUT, sessionId, null, null, 0L, workspacePath, 0L);
    }

    public static SandboxOpRequestDTO reset(String correlationId, String targetAgentShortName, String sessionId) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.RESET, sessionId, null, null, 0L, null, 0L);
    }

    public static SandboxOpRequestDTO destroy(String correlationId, String targetAgentShortName, String sessionId) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.DESTROY, sessionId, null, null, 0L, null, 0L);
    }

    public static SandboxOpRequestDTO list(String correlationId, String targetAgentShortName) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, SandboxOp.LIST, null, null, null, 0L, null, 0L);
    }

    public SandboxOpRequestDTO withDeadline(Duration budget) {
        return new SandboxOpRequestDTO(correlationId, targetAgentShortName, op, sessionId, sessionSpec, command, timeoutSeconds, workspacePath,
                Instant.now().plus(budget).toEpochMilli());
    }
}
