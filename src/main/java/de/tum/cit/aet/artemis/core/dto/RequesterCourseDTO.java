package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequesterCourseDTO(String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate) {

    public static RequesterCourseDTO of(Course course) {
        return new RequesterCourseDTO(course.getTitle(), course.getShortName(), course.getSemester(), course.getStartDate(), course.getEndDate());
    }
}
