package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for Online Courses.
 * Contains essential fields for representing an online course in the system.
 * Utilized primarily for transferring online course data in web requests.
 *
 * @param id             The unique identifier of the online course.
 * @param title          The title of the online course.
 * @param shortName      The short name of the online course.
 * @param registrationId The registration ID associated with the LTI platform for the course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OnlineCourseDTO(Long id, String title, String shortName, String registrationId) {
}
