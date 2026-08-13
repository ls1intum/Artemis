package de.tum.cit.aet.artemis.core.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a password change required data - current and new password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PasswordChangeDTO(String currentPassword, String newPassword, @Nullable CredentialRevocationChoiceDTO revokeCredentials) {

    /**
     * @return the caller's revocation choice, or a choice that revokes nothing when the request did not express one
     */
    public CredentialRevocationChoiceDTO revokeCredentialsOrNone() {
        return revokeCredentials != null ? revokeCredentials : CredentialRevocationChoiceDTO.none();
    }
}
