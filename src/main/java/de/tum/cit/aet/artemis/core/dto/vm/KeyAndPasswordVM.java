package de.tum.cit.aet.artemis.core.dto.vm;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;

/**
 * View Model object for storing the user's key and password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class KeyAndPasswordVM {

    private String key;

    private String newPassword;

    @Nullable
    private CredentialRevocationChoiceDTO revokeCredentials;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    @Nullable
    public CredentialRevocationChoiceDTO getRevokeCredentials() {
        return revokeCredentials;
    }

    public void setRevokeCredentials(@Nullable CredentialRevocationChoiceDTO revokeCredentials) {
        this.revokeCredentials = revokeCredentials;
    }

    /**
     * The revocation choice for this reset, defaulting to revoking everything when the request does not express one.
     * <p>
     * Deliberately a different default from {@link de.tum.cit.aet.artemis.core.dto.PasswordChangeDTO}, which defaults to
     * revoking nothing. A password change proves knowledge of the current password; completing a reset only proves
     * control of the mailbox, which is the weaker claim to the account. So the safe outcome is the default here and the
     * user opts out, rather than opting in - and a client that does not send a choice at all keeps the stricter
     * behaviour.
     *
     * @return the caller's choice, or a choice that revokes every credential type
     */
    public CredentialRevocationChoiceDTO revokeCredentialsOrAll() {
        return revokeCredentials != null ? revokeCredentials : new CredentialRevocationChoiceDTO(true, true, true);
    }
}
