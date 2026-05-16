package de.tum.cit.aet.artemis.core.config.audit;

public class AuditEventConstants {

    public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

    public static final String AUTHENTICATION_PASSKEY_SUCCESS = "AUTHENTICATION_PASSKEY_SUCCESS";

    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";

    public static final String SAML2_EXTERNAL_REDIRECT_SUCCESS = "SAML2_EXTERNAL_REDIRECT_SUCCESS";

    public static final String SAML2_EXTERNAL_REDIRECT_FAILURE = "SAML2_EXTERNAL_REDIRECT_FAILURE";

    /**
     * Utility class, should not be instantiated.
     */
    private AuditEventConstants() {
    }

}
