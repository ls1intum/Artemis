package de.tum.cit.aet.artemis.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.Organization;

/**
 * Request DTO for creating or updating an organization.
 *
 * @param id           the organization identifier; optional for creation and required for updates
 * @param name         the full name of the organization
 * @param shortName    the short name of the organization
 * @param url          the optional website URL of the organization
 * @param description  the optional description of the organization
 * @param logoUrl      the optional URL of the organization logo
 * @param emailPattern the pattern used to associate users by email address
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrganizationInputDTO(@Nullable Long id, @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 50) String shortName, @Nullable String url,
        @Nullable String description, @Nullable String logoUrl, @NotBlank String emailPattern) {

    /**
     * Creates a detached organization entity containing only the accepted request fields.
     *
     * @return a detached organization entity for the organization service
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
