package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.validator.Base64Url;

/**
 * DTO for passkey information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PasskeyDTO(@NotNull @Base64Url String credentialId, String label, Instant created, Instant lastUsed, boolean isSuperAdminApproved) {
}
