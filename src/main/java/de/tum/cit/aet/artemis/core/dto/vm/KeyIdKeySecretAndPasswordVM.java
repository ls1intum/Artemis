package de.tum.cit.aet.artemis.core.dto.vm;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;

/**
 * View Model object for storing the user's key id, key secret and password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KeyIdKeySecretAndPasswordVM(String keyId, String keySecret, String newPassword, @Nullable CredentialRevocationChoiceDTO revokeCredentials) {

    public CredentialRevocationChoiceDTO revokeCredentialsOrAll() {
        return revokeCredentials != null ? revokeCredentials : new CredentialRevocationChoiceDTO(true, true, true);
    }
}
