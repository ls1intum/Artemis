package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What every method of a JSON-RPC request shares of its parameters.
 *
 * @param protocolVersion the protocol version a client asks for in {@code initialize}
 * @param name            the tool a client calls in {@code tools/call}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcParamsDTO(String protocolVersion, String name) {
}
