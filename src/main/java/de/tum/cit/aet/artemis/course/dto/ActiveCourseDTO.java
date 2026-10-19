package de.tum.cit.aet.artemis.course.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveCourseDTO(long id, String title, String shortName, String semester, long numberOfStudents) {

    public ActiveCourseDTO {
        if (title == null) {
            title = "Course" + Objects.requireNonNullElse(shortName, id);
        }
    }
}
