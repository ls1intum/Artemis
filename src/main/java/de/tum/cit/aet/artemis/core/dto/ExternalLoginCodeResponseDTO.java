package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response containing the one-time external-login code the web app redirects to the extension with.
 *
 * @param code the one-time code
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExternalLoginCodeResponseDTO(String code) {
}
