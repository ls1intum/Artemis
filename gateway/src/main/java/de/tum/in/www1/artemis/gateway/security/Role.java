package de.tum.in.www1.artemis.gateway.security;

/**
 * Constants for Spring Security authorities.
 */
public enum Role {

    // NOTE: we will soon rename "USER" to "STUDENT" in the database and add a new role "EDITOR"
    ADMIN("ADMIN"), INSTRUCTOR("INSTRUCTOR"), EDITOR("EDITOR"), TEACHING_ASSISTANT("TA"), STUDENT("USER"), ANONYMOUS("ANONYMOUS");

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
