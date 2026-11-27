package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The {@code data} of the error that answers a request of a protocol version the server does not speak.
 *
 * @param supported the versions the server does speak
 * @param requested the version the client asked for, if it said
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UnsupportedProtocolVersionDTO(List<String> supported, String requested) {
}
