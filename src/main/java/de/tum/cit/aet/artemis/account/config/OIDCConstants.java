package de.tum.cit.aet.artemis.account.config;

public final class OIDCConstants {

    /**
     * Attribute keys of the parameters the login was started with, stored in the attributes of the OAuth2 authorization request.
     */
    public static final String OIDC_REDIRECT_TARGET_ATTRIBUTE = "OIDC_REDIRECT";

    public static final String OIDC_CODE_CHALLENGE_ATTRIBUTE = "OIDC_CODE_CHALLENGE";

    public static final String OIDC_REMEMBER_ME_ATTRIBUTE = "OIDC_REMEMBER_ME";

    /**
     * The request attribute that holds the authorization request the callback was matched to, for the success and failure handlers.
     */
    public static final String OIDC_AUTHORIZATION_REQUEST_ATTRIBUTE = "OIDC_AUTHORIZATION_REQUEST";

    /**
     * The cookie that binds the callback of the identity provider to the browser that started the login.
     */
    public static final String OIDC_STATE_COOKIE_NAME = "oidc-state";

    public static final String VS_CODE_REDIRECT_TARGET = "vscode";

    public static final String VS_CODE_DEEP_LINK_BASE = "vscode://aet-tum.iris-thaumantias/auth-callback";

    public static final String IOS_REDIRECT_TARGET = "ios";

    public static final String IOS_DEEP_LINK_BASE = "de.tum.cit.ase.artemis://oauth2callback";

    private OIDCConstants() {
    }
}
