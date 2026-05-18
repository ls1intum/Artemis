package de.tum.cit.aet.artemis.core.domain;

public enum CourseRole {

    // WARNING: declaration order is load-bearing. isAtLeast() uses ordinal() comparison,
    // so roles must stay ordered from least to most privileged. Do not reorder these constants.
    STUDENT, TEACHING_ASSISTANT, EDITOR, INSTRUCTOR;

    public boolean isAtLeast(CourseRole minimum) {
        return this.ordinal() >= minimum.ordinal();
    }
}
