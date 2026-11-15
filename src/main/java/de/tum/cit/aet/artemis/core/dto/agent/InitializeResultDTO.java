package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of {@code initialize}.
 *
 * @param protocolVersion the protocol version the server will speak
 * @param capabilities    what the server offers
 * @param serverInfo      which server it is
 * @param instructions    what the model should know before it uses the tools
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InitializeResultDTO(String protocolVersion, CapabilitiesDTO capabilities, ServerInfoDTO serverInfo, String instructions) {
}
