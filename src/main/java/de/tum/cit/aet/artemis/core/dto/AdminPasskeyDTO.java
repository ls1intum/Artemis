package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.validator.Base64Url;

/**
 * A DTO representing a passkey credential with user information for administrative purposes.
 * This extends the basic PasskeyDTO with additional user details.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AdminPasskeyDTO(@NotNull @Base64Url String credentialId, String label, Instant created, Instant lastUsed, boolean isSuperAdminApproved, Long userId, String userLogin,
        String userName) {
}
