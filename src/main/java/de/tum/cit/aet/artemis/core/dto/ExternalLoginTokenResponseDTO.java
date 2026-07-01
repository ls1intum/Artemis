package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response containing the full Artemis JWT for an external client.
 * <p>
 * Serialized as {@code access_token} to match the documented contract and the existing
 * {@code /api/core/public/authenticate} endpoint, so external clients can read a single, consistent field.
 *
 * @param accessToken the JWT (to be stored and sent as the {@code jwt} cookie by the client)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExternalLoginTokenResponseDTO(@JsonProperty("access_token") String accessToken) {
}
