package de.tum.cit.aet.artemis.account.config;

public final class OIDCConstants {

    public static final String OIDC_REDIRECT_TARGET_SESSION_KEY = "OIDC_REDIRECT";

    public static final String OIDC_CODE_CHALLENGE_SESSION_KEY = "OIDC_CODE_CHALLENGE";

    public static final String OIDC_REMEMBER_ME_SESSION_KEY = "OIDC_REMEMBER_ME";

    public static final String VS_CODE_REDIRECT_TARGET = "vscode";

    public static final String VS_CODE_DEEP_LINK_BASE = "vscode://aet-tum.iris-thaumantias/auth-callback";

    public static final String IOS_REDIRECT_TARGET = "ios";

    public static final String IOS_DEEP_LINK_BASE = "de.tum.cit.ase.artemis://oauth2callback";

    private OIDCConstants() {
    }
}
