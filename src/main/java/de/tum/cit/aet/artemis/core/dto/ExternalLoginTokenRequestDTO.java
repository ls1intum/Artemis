package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to exchange a one-time code (with its PKCE verifier) for a full JWT.
 *
 * @param code         the one-time code received via the extension callback
 * @param codeVerifier the PKCE code verifier matching the challenge sent at issue time
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExternalLoginTokenRequestDTO(String code, String codeVerifier) {
}
