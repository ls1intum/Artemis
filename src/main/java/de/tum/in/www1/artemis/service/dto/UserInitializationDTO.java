package de.tum.in.www1.artemis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a newly created password. If unset, no password was created
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserInitializationDTO(String password) {
}
