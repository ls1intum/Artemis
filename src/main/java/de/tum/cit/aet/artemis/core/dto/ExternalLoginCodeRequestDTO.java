package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to issue a one-time external-login code.
 *
 * @param codeChallenge the PKCE S256 code challenge (base64url, no padding)
 * @param callback      the extension callback URI to redirect to with the code
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExternalLoginCodeRequestDTO(String codeChallenge, String callback) {
}
