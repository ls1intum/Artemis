package de.tum.cit.aet.artemis.core.service.agent;

import org.jspecify.annotations.Nullable;

/**
 * The headers with which the Streamable HTTP transport of the protocol mirrors parts of the message, so that a gateway can route it
 * without reading the body. The server compares them with the body.
 *
 * @param protocolVersion the {@code MCP-Protocol-Version} header
 * @param method          the {@code Mcp-Method} header
 * @param name            the {@code Mcp-Name} header, which a {@code tools/call} carries
 */
public record AgentMcpHeaders(@Nullable String protocolVersion, @Nullable String method, @Nullable String name) {
}
