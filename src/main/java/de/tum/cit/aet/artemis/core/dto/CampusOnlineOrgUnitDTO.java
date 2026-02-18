package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineOrgUnit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgUnitDTO(Long id, @NotBlank @Size(max = 50) String externalId, @NotBlank @Size(max = 255) String name) {

    public static CampusOnlineOrgUnitDTO fromEntity(CampusOnlineOrgUnit entity) {
        return new CampusOnlineOrgUnitDTO(entity.getId(), entity.getExternalId(), entity.getName());
    }
}
