package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.core.dto.validator.Base64Url;

/**
 * DTO for passkey information.
 */
public record PasskeyDTO(@NotNull @Base64Url String credentialId, String label, Instant created, Instant lastUsed) {
}
