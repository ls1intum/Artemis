package de.tum.in.www1.artemis.security;

/**
 * Constants for Spring Security authorities.
 */
public enum Role {

    ADMIN("ROLE_ADMIN"), INSTRUCTOR("ROLE_INSTRUCTOR"), TEACHING_ASSISTANT("ROLE_TA"), USER("ROLE_USER"), ANONYMOUS("ROLE_ANONYMOUS");

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }
}
