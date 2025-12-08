package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for TUM Live authentication request.
 *
 * @param username the TUM username
 * @param password the TUM password
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveLoginRequestDTO(@NotBlank String username, @NotBlank String password) {
}
