package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What every request of the Model Context Protocol carries in {@code params._meta}. The protocol keeps no session, so a request says
 * for itself which version it speaks and what the client can do.
 *
 * @param protocolVersion    the protocol version of the request; it has to match the {@code MCP-Protocol-Version} header
 * @param clientCapabilities what the client supports; an empty object means nothing optional. Read as an object, because only its
 *                               presence matters to a server that offers tools alone
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RequestMetaDTO(@JsonProperty("io.modelcontextprotocol/protocolVersion") String protocolVersion,
        @JsonProperty("io.modelcontextprotocol/clientCapabilities") Object clientCapabilities) {
}
