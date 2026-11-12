package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generic DTO for displaying a user in a registration modal (search + register flow).
 * Used wherever an instructor needs to search Artemis users and register them into some entity (exam, tutorial group, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserForRegistrationDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String login,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String email, String registrationNumber, String profilePictureUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean isRegistered) {
}
