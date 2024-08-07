package de.tum.in.www1.artemis.domain.vcstokens;

public enum AuthenticationMechanism {
    /**
     * The user used password to authenticate to the LocalVC
     */
    PASSWORD,
    /**
     * The user used the participation+user token to authenticate to the LocalVC
     */
    PARTICIPATION_VCS_ACCESS_TOKEN,
    /**
     * The user used the user token to authenticate to the LocalVC
     */
    USER_VCS_ACCESS_TOKEN,
    /**
     * The user used SSH user token to authenticate to the LocalVC
     */
    SSH
}
