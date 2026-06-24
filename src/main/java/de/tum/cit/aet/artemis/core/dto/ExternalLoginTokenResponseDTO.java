package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response containing the full Artemis JWT for an external client.
 *
 * @param accessToken the JWT (to be stored and sent as the {@code jwt} cookie by the client)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExternalLoginTokenResponseDTO(String accessToken) {
}
