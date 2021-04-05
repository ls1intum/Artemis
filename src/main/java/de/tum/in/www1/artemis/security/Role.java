package de.tum.in.www1.artemis.security;

/**
 * Constants for Spring Security authorities.
 */
public enum Role {

    ADMIN("ADMIN"), INSTRUCTOR("INSTRUCTOR"), TEACHING_ASSISTANT("TA"), USER("USER"), ANONYMOUS("ANONYMOUS");

    public static final String ROLE_PREFIX = "ROLE_";

    private final String role;

    Role(String role) {
        this.role = role;
    }

    public String getAuthority() {
        return ROLE_PREFIX + role;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return role;
    }
}
