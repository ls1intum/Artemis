package de.tum.cit.aet.artemis.core.domain;

import de.tum.cit.aet.artemis.core.security.Role;

public enum CourseRole {

    // WARNING: declaration order is load-bearing. isAtLeast() uses ordinal() comparison,
    // so roles must stay ordered from least to most privileged. Do not reorder these constants.
    STUDENT, TEACHING_ASSISTANT, EDITOR, INSTRUCTOR;

    public boolean isAtLeast(CourseRole minimum) {
        return this.ordinal() >= minimum.ordinal();
    }

    public static CourseRole fromRole(Role role) {
        return switch (role) {
            case STUDENT -> STUDENT;
            case TEACHING_ASSISTANT -> TEACHING_ASSISTANT;
            case EDITOR -> EDITOR;
            case INSTRUCTOR -> INSTRUCTOR;
            default -> throw new IllegalArgumentException("No CourseRole for security Role: " + role);
        };
    }
}
