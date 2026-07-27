package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * The reply a remote build agent publishes after performing a {@link SandboxOpRequestDTO}. Broadcast over the {@code hyperion-sandbox-responses}
 * {@link de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic} and matched back to the blocked caller by {@link #correlationId}; on failure
 * {@link #success} is {@code false} and {@link #errorMessage} carries a short description the caller rethrows as a session-fatal exception.
 *
 * @param correlationId the id of the {@link SandboxOpRequestDTO} this response answers
 * @param sessionId     the created container id for {@link SandboxOp#CREATE}; echoed back otherwise (may be {@code null})
 * @param execResult    the captured exit code and bounded stdout/stderr for {@link SandboxOp#EXEC}; {@code null} otherwise
 * @param sessions      the live session snapshot for {@link SandboxOp#LIST}; {@code null} otherwise
 * @param errorMessage  a short error description when {@link #success} is {@code false}; {@code null} on success
 */
public record SandboxOpResponseDTO(String correlationId, boolean success, String sessionId, SandboxExecResultDTO execResult, List<GenerationSandboxSessionDTO> sessions,
        String errorMessage) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A success response carrying no further data (used by {@link SandboxOp#COPY_IN} and {@link SandboxOp#DESTROY}). */
    public static SandboxOpResponseDTO ok(String correlationId, String sessionId) {
        return new SandboxOpResponseDTO(correlationId, true, sessionId, null, null, null);
    }

    /** A {@link SandboxOp#CREATE} success response carrying the new container id as the session handle. */
    public static SandboxOpResponseDTO created(String correlationId, String containerId) {
        return new SandboxOpResponseDTO(correlationId, true, containerId, null, null, null);
    }

    /** An {@link SandboxOp#EXEC} success response carrying the captured exit code and bounded output. */
    public static SandboxOpResponseDTO exec(String correlationId, String sessionId, SandboxExecResultDTO execResult) {
        return new SandboxOpResponseDTO(correlationId, true, sessionId, execResult, null, null);
    }

    /**
     * A {@link SandboxOp#COPY_OUT} success response. The repacked tar bytes ride the keyed staging map (keyed by {@code correlationId}), not the response itself, so only the
     * originating core node fetches them.
     */
    public static SandboxOpResponseDTO copiedOut(String correlationId, String sessionId) {
        return new SandboxOpResponseDTO(correlationId, true, sessionId, null, null, null);
    }

    public static SandboxOpResponseDTO sessions(String correlationId, List<GenerationSandboxSessionDTO> sessions) {
        return new SandboxOpResponseDTO(correlationId, true, null, null, List.copyOf(sessions), null);
    }

    /** A failure response carrying a short error description for the blocked caller to rethrow. */
    public static SandboxOpResponseDTO failure(String correlationId, String errorMessage) {
        return new SandboxOpResponseDTO(correlationId, false, null, null, null, errorMessage);
    }
}
