package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MaintenanceEmailRecipientDTO(Long id, String email, String langKey, String firstName, String lastName) {
}
