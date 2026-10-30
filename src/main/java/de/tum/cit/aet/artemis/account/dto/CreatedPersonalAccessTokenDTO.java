package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The response to creating a personal access token. This is the only time the secret is available.
 *
 * @param token  the stored token
 * @param secret the secret to send as a bearer token; it cannot be recovered later
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreatedPersonalAccessTokenDTO(PersonalAccessTokenDTO token, String secret) {
}
