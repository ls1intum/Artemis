package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Relay request broadcast to hosting agents and executed only by {@code targetAgentShortName}. Retries retain the correlation ID and deadline so the handler can replay a
 * cached response instead of repeating an operation. Copy payloads are staged separately under the correlation ID.
 *
 * @param correlationId        request idempotency key
 * @param targetAgentShortName owning build agent
 * @param sessionId            container id; null for CREATE and LIST
 * @param sessionSpec          CREATE specification; otherwise null
 * @param command              EXEC argument vector; otherwise null
 * @param timeoutSeconds       EXEC timeout
 * @param workspacePath        COPY_IN destination or COPY_OUT source inside the container
 * @param deadlineEpochMillis  deadline after which execution must not start
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
