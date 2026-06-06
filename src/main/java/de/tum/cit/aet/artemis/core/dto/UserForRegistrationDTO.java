package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic DTO for displaying a user in a registration modal (search + register flow).
 * Used wherever an instructor needs to search Artemis users and register them into some entity (exam, tutorial group, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserForRegistrationDTO(Long id, String login, String name, String email, String registrationNumber, String profilePictureUrl, boolean isRegistered) {
}
