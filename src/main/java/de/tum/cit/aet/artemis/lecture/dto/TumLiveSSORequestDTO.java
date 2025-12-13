package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for TUM Live SSO authentication request.
 * Used when Artemis authenticates users via SAML and requests upload tokens from TUM Live.
 *
 * @param lrzId         the LRZ ID (e.g., ge12abc)
 * @param matrNr        the matriculation number (optional)
 * @param firstName     the user's first name
 * @param lastName      the user's last name (optional)
 * @param email         the user's email (optional)
 * @param artemisApiKey the shared secret between Artemis and TUM Live
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveSSORequestDTO(@NotBlank String lrzId, String matrNr, @NotBlank String firstName, String lastName, String email, @NotBlank String artemisApiKey) {
}
