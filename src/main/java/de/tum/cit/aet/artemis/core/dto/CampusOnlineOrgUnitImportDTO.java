package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgUnitImportDTO(@NotBlank @Size(max = 50) String externalId, @NotBlank @Size(max = 255) String name) {
}
