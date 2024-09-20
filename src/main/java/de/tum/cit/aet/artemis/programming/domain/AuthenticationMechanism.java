package de.tum.cit.aet.artemis.programming.domain;

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
    SSH,
    /**
     * The user used the artemis client code editor to authenticate to the LocalVC
     */
    CODE_EDITOR
}
