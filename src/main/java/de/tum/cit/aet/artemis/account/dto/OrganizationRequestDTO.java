package de.tum.cit.aet.artemis.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for creating and updating an organization.
 * <p>
 * Separate from {@link OrganizationDTO}, which is a read model. The endpoints take this rather than the
 * {@code Organization} entity so that the writable fields are stated explicitly. Mapping to the entity lives in the
 * organization service, keeping this DTO limited to transport data.
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
public record OrganizationRequestDTO(@Nullable Long id, @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 50) String shortName, @Nullable String url,
        @Nullable String description, @Nullable String logoUrl, @NotBlank String emailPattern) {
}
