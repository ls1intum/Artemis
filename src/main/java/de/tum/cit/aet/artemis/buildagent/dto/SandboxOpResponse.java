package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * The reply a remote build agent publishes after performing a {@link SandboxOpRequest}.
 * <p>
 * Responses are broadcast over the {@code hyperion-sandbox-responses}
 * {@link de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic}; the originating core node matches them back to the blocked caller by
 * {@link #correlationId} and ignores responses for correlation ids it does not own. On failure {@link #success} is {@code false} and {@link #errorMessage} carries a short
 * description so the caller can throw a meaningful exception (which the orchestrator treats as session-fatal).
 *
 * @param correlationId the id of the {@link SandboxOpRequest} this response answers
 * @param success       whether the operation completed successfully
 * @param sessionId     the created container id for {@link SandboxOp#CREATE}; echoed back otherwise (may be {@code null})
 * @param execResult    the captured exit code and bounded stdout/stderr for {@link SandboxOp#EXEC}; {@code null} otherwise
 * @param payload       the tar bytes for {@link SandboxOp#COPY_OUT}; {@code null} otherwise
 * @param errorMessage  a short error description when {@link #success} is {@code false}; {@code null} on success
 */
public record SandboxOpResponse(String correlationId, boolean success, String sessionId, SandboxExecResult execResult, byte[] payload, String errorMessage)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Builds a success response carrying no further data (used by {@link SandboxOp#COPY_IN} and {@link SandboxOp#DESTROY}).
     *
     * @param correlationId the request correlation id
     * @param sessionId     the session id the operation acted on
     * @return a success response with no exec result and no payload
     */
    public static SandboxOpResponse ok(String correlationId, String sessionId) {
        return new SandboxOpResponse(correlationId, true, sessionId, null, null, null);
    }

    /**
     * Builds a failure response.
     *
     * @param correlationId the request correlation id
     * @param errorMessage  a short description of the failure
     * @return a failure response
     */
    public static SandboxOpResponse failure(String correlationId, String errorMessage) {
        return new SandboxOpResponse(correlationId, false, null, null, null, errorMessage);
    }
}
