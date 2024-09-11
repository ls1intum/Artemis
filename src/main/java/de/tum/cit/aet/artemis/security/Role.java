package de.tum.cit.aet.artemis.security;

/**
 * Constants for Spring Security authorities.
 */
public enum Role {

    // NOTE: we will soon rename "USER" to "STUDENT" in the database
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

    /**
     * creates a role from a string
     *
     * @param courseGroup the group of the course
     * @return the corresponding role
     */
    public static Role fromString(String courseGroup) {
        return switch (courseGroup.toLowerCase()) {
            case "students" -> STUDENT;
            case "tutors" -> TEACHING_ASSISTANT;
            case "instructors" -> INSTRUCTOR;
            case "editors" -> EDITOR;
            default -> ANONYMOUS;
        };
    }
}
