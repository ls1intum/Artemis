package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineOrgUnit;

/**
 * DTO for a CAMPUSOnline organizational unit, used for CRUD operations in the admin API.
 *
 * @param id         the internal database ID (null for creation requests)
 * @param externalId the external ID in the CAMPUSOnline system (max 50 characters)
 * @param name       the human-readable name of the organizational unit (max 255 characters)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineOrgUnitDTO(Long id, @NotBlank @Size(max = 50) String externalId, @NotBlank @Size(max = 255) String name) {

    /**
     * Creates a DTO from a JPA entity.
     *
     * @param entity the organizational unit entity
     * @return a new DTO with the entity's field values
     */
    public static CampusOnlineOrgUnitDTO fromEntity(CampusOnlineOrgUnit entity) {
        return new CampusOnlineOrgUnitDTO(entity.getId(), entity.getExternalId(), entity.getName());
    }
}
