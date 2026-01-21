package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * Data Transfer Object for Online Courses.
 * Contains essential fields for representing an online course in the system.
 * Utilized primarily for transferring online course data in web requests.
 *
 * @param id               The unique identifier of the online course.
 * @param title            The title of the online course.
 * @param shortName        The short name of the online course.
 * @param registrationId   The registration ID associated with the LTI platform for the course.
 * @param startDate        The date and time when the course begins (in ZonedDateTime format).
 * @param endDate          The date and time when the course ends (in ZonedDateTime format).
 * @param description      A brief textual summary or overview of the course content.
 * @param numberOfStudents The current number of students enrolled in the course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineCourseDTO(Long id, String title, String shortName, String registrationId, ZonedDateTime startDate, ZonedDateTime endDate, String description,
        Long numberOfStudents) {

    public static OnlineCourseDTO from(Course course) {
        return new OnlineCourseDTO(course.getId(), course.getTitle(), course.getShortName(),
                course.getOnlineCourseConfiguration().getLtiPlatformConfiguration().getRegistrationId(), course.getStartDate(), course.getEndDate(), course.getDescription(),
                course.getNumberOfStudents());
    }
}
