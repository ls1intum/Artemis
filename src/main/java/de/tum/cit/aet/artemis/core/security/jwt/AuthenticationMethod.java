package de.tum.cit.aet.artemis.core.security.jwt;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

public enum AuthenticationMethod {

    PASSKEY("passkey"), PASSWORD("password"), SAML2("saml2");

    private final String method;

    AuthenticationMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    /**
     * Returns the authentication method based on the provided Authentication object.
     *
     * @param authentication the Authentication object
     * @return the corresponding AuthenticationMethod, or null if not found
     */
    public static AuthenticationMethod fromAuthentication(Authentication authentication) {
        return switch (authentication) {
            case WebAuthnAuthentication webAuthnAuthentication -> AuthenticationMethod.PASSKEY;
            case UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken -> AuthenticationMethod.PASSWORD;
            case Saml2Authentication saml2Authentication -> AuthenticationMethod.SAML2;
            case null, default -> null;
        };
    }

    /**
     * Returns the authentication method based on the provided method string.
     *
     * @param method the method string
     * @return the corresponding AuthenticationMethod
     * @throws IllegalArgumentException if the method is not recognized
     */
    public static AuthenticationMethod fromMethod(String method) {
        for (AuthenticationMethod authMethod : values()) {
            if (authMethod.method.equalsIgnoreCase(method)) {
                return authMethod;
            }
        }
        throw new IllegalArgumentException("Unknown authentication method: " + method);
    }
}
