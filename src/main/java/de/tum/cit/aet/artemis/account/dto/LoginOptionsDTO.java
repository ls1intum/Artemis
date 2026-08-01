package de.tum.cit.aet.artemis.account.dto;

/**
 * DTO representing the dynamic login options for a user.
 * * @param loginMethod The method the client side should display ("PASSWORD", "OIDC", "SAML2").
 *
 * @param idpName The display name of the identity provider (e.g., "TUM Login") to render on the SSO button.
 */
public record LoginOptionsDTO(LoginMethod loginMethod, String idpName, String debugReason) {

    public enum LoginMethod {
        PASSWORD, OIDC, SAML2
    }
}
