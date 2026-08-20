package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.Organization;

/**
 * DTO representing an organization and, when requested, its aggregated user and course counts.
 *
 * @param id              the unique identifier of the persisted organization
 * @param name            the full name of the organization
 * @param shortName       the short name of the organization
 * @param url             the optional website URL of the organization
 * @param description     the optional description of the organization
 * @param emailPattern    the pattern used to associate users by email address
 * @param logoUrl         the optional URL of the organization logo
 * @param numberOfUsers   the number of users in the organization, or {@code null} when counts were not requested
 * @param numberOfCourses the number of courses in the organization, or {@code null} when counts were not requested
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationDTO(Long id, String name, String shortName, @Nullable String url, @Nullable String description, String emailPattern, @Nullable String logoUrl,
        @Nullable Long numberOfUsers, @Nullable Long numberOfCourses) {

    /**
     * Creates an organization DTO without aggregate counts.
     *
     * @param organization the persisted organization to map
     * @return the organization DTO with absent aggregate counts
     */
    public static OrganizationDTO of(Organization organization) {
        return of(organization, null, null);
    }

    /**
     * Creates an organization DTO with optional aggregate counts.
     *
     * @param organization    the persisted organization to map
     * @param numberOfUsers   the number of users, or {@code null} when counts were not requested
     * @param numberOfCourses the number of courses, or {@code null} when counts were not requested
     * @return the mapped organization DTO
     */
    public static OrganizationDTO of(Organization organization, @Nullable Long numberOfUsers, @Nullable Long numberOfCourses) {
        return new OrganizationDTO(organization.getId(), organization.getName(), organization.getShortName(), organization.getUrl(), organization.getDescription(),
                organization.getEmailPattern(), organization.getLogoUrl(), numberOfUsers, numberOfCourses);
    }
}
