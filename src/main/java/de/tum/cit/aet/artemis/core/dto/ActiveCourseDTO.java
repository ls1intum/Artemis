package de.tum.cit.aet.artemis.core.dto;

import java.util.Objects;

public record ActiveCourseDTO(long id, String title, String shortName, String semester, long numberOfStudents) {

    public static final String NO_SEMESTER_TAG = "No semester";

    public ActiveCourseDTO {
        if (semester == null) {
            semester = NO_SEMESTER_TAG;
        }
        if (title == null) {
            title = "Course" + Objects.requireNonNullElse(shortName, id);
        }
    }
}
