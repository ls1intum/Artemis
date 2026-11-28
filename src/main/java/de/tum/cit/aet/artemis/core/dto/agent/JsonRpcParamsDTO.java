package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What every method of a JSON-RPC request shares of its parameters.
 *
 * @param protocolVersion the protocol version a client of an older revision asks for in {@code initialize}, which is answered with an error naming the supported ones
 * @param name            the tool a client calls in {@code tools/call}
 * @param meta            the version and the capabilities of the client, which every request carries
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcParamsDTO(String protocolVersion, String name, @JsonProperty("_meta") RequestMetaDTO meta) {
}
