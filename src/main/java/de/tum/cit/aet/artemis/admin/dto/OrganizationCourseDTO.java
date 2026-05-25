package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a course linked to an organization with minimal course information
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationCourseDTO(Long id, String title, String shortName) {
}
