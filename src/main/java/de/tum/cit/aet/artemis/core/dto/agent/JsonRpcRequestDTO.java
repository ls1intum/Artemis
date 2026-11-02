package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A JSON-RPC 2.0 request as the MCP endpoint reads it. The arguments of a tool call are not part of it: they differ per tool and are
 * read in a second step, see {@link ToolCallRequestDTO}.
 *
 * @param jsonrpc the protocol version, which has to be {@code 2.0}
 * @param id      what the answer repeats; absent for a notification, which is not answered
 * @param method  what the agent asks for, such as {@code tools/call}
 * @param params  what the method needs, as far as every method shares it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcRequestDTO(String jsonrpc, JsonRpcIdDTO id, String method, JsonRpcParamsDTO params) {
}
