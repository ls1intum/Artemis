package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Class representing the number of users and courses mapped to a specific organization
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationCountDTO(Long organizationId, Long numberOfUsers, Long numberOfCourses) {
}
