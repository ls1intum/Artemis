package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for bulk-importing organizational units from a CSV file.
 * Unlike {@link CampusOnlineOrgUnitDTO}, this does not include an ID since it is used only for creation.
 *
 * @param externalId the external ID in the CAMPUSOnline system (max 50 characters)
 * @param name       the human-readable name of the organizational unit (max 255 characters)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgUnitImportDTO(@NotBlank @Size(max = 50) String externalId, @NotBlank @Size(max = 255) String name) {
}
