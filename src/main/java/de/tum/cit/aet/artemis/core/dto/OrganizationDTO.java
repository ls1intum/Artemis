package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing an organization with aggregated counts of its users and courses
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationDTO(Long id, String name, String shortName, String emailPattern, Long numberOfUsers, Long numberOfCourses) {
}
