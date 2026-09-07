package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.Organization;

/**
 * DTO for creating and updating an organization.
 * <p>
 * Separate from {@link OrganizationDTO}, which is a read model: it carries the aggregated user and course counts and
 * omits the url and description, so it cannot serve as a request body.
 * <p>
 * The endpoints take this rather than the {@code Organization} entity so that the writable fields are stated in one
 * place instead of being whatever the entity happens to expose.
 *
 * @param id           the organization id; required when updating, {@code null} when creating
 * @param name         the display name
 * @param shortName    the short name
 * @param url          a link to the organization
 * @param description  a description of the organization
 * @param logoUrl      a link to the organization's logo
 * @param emailPattern the regular expression matching the email addresses of the organization's members
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationRequestDTO(Long id, String name, String shortName, String url, String description, String logoUrl, String emailPattern) {

    /**
     * Build the entity this DTO describes.
     *
     * @return an organization carrying the values of this DTO
     */
    public Organization toEntity() {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setShortName(shortName);
        organization.setUrl(url);
        organization.setDescription(description);
        organization.setLogoUrl(logoUrl);
        organization.setEmailPattern(emailPattern);
        return organization;
    }
}
