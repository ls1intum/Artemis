package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Class representing the number of users and courses mapped to a specific organization
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OrganizationCountDTO {

    public Long organizationId;

    public Long numberOfUsers;

    public Long numberOfCourses;

    public OrganizationCountDTO(Long organizationId, Long numberOfUsers, Long numberOfCourses) {
        this.organizationId = organizationId;
        this.numberOfUsers = numberOfUsers;
        this.numberOfCourses = numberOfCourses;
    }

    public Long getNumberOfUsers() {
        return numberOfUsers;
    }

    public Long getNumberOfCourses() {
        return numberOfCourses;
    }
}
