package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Class representing the number of users and courses mapped to a specific organization
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationDTO(Long id, String name, String shortName, String url, String description, String logoUrl, String emailPattern, Long numberOfUsers,
        Long numberOfCourses) {
}
