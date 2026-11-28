package de.tum.cit.aet.artemis.core.service.agent;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.dto.agent.JsonRpcResponseDTO;

/**
 * What the MCP endpoint answers to one message. The status is part of it, because the Streamable HTTP transport of the protocol
 * prescribes one for each kind of failure.
 *
 * @param status the HTTP status of the answer
 * @param body   the JSON-RPC answer, or null for a notification, which has none
 */
public record AgentMcpAnswer(int status, @Nullable JsonRpcResponseDTO<?> body) {
}
