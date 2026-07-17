package de.tum.cit.aet.artemis.account.dto;

/**
 * DTO representing the dynamic login options for a user.
 * * @param loginMethod The method the client side should display ("PASSWORD", "OIDC", "SAML").
 *
 * @param idpName The display name of the identity provider (e.g., "TUM Login") to render on the SSO button.
 */
public record LoginOptionsDTO(String loginMethod, String idpName) {
}
