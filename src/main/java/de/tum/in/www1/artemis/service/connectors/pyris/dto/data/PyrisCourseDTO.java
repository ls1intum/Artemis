package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

public record PyrisCourseDTO(long id, String name, String description, String organizationalDetails, String language, ProgrammingLanguage defaultProgrammingLanguage,
        ZonedDateTime startDate, ZonedDateTime endDate, Boolean onlineCourse) {

    public PyrisCourseDTO(Course course) {
        this(course.getId(), course.getTitle(), course.getDescription(), course.getOrganizationalDetails(), "English", course.getDefaultProgrammingLanguage(),
                course.getStartDate(), course.getEndDate(), course.isOnlineCourse());
    }
}
