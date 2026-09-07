package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Relay result matched to its request by {@code correlationId}. Only the fields for the requested operation are populated; copy-out bytes are staged separately.
 * A failed response carries {@code errorMessage} instead of a result.
 */
public record SandboxOpResponseDTO(String correlationId, boolean success, String sessionId, SandboxExecResultDTO execResult, List<GenerationSandboxSessionDTO> sessions,
        String errorMessage) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static SandboxOpResponseDTO ok(String correlationId, String sessionId) {
        return new SandboxOpResponseDTO(correlationId, true, sessionId, null, null, null);
    }

    public static SandboxOpResponseDTO created(String correlationId, String containerId) {
        return new SandboxOpResponseDTO(correlationId, true, containerId, null, null, null);
    }

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

    public static SandboxOpResponseDTO failure(String correlationId, String errorMessage) {
        return new SandboxOpResponseDTO(correlationId, false, null, null, null, errorMessage);
    }
}
