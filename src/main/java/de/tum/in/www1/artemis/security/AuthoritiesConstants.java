package de.tum.in.www1.artemis.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String TEACHING_ASSISTANT = "ROLE_TA";

    public static final String INSTRUCTOR = "ROLE_INSTRUCTOR";

    public static final String USER = "ROLE_USER";

    private AuthoritiesConstants() {
    }
}
