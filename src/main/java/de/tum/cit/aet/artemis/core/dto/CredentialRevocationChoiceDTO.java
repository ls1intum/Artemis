package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Which of the user's other credentials should be revoked alongside a password change.
 * <p>
 * The choice belongs to the user because the right answer depends on something only they know: whether the old password
 * may have been seen by someone else. A routine rotation does not warrant losing the authenticators and keys they have
 * enrolled on their devices, whereas a suspected leak or theft does - a password change alone would not end an intrusion,
 * because each of these credentials is enough on its own to keep using the account.
 *
 * @param passkeys        delete all registered passkeys, so any authenticator enrolled by someone else stops working
 * @param sshKeys         delete all SSH keys, so repository access over SSH stops working
 * @param vcsAccessTokens clear the personal VCS access token and delete the participation and repository tokens, so
 *                            repository access over HTTPS stops working
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CredentialRevocationChoiceDTO(boolean passkeys, boolean sshKeys, boolean vcsAccessTokens) {

    /**
     * @return a choice that revokes nothing, used when a request does not express one
     */
    public static CredentialRevocationChoiceDTO none() {
        return new CredentialRevocationChoiceDTO(false, false, false);
    }

    /**
     * @return {@code true} if at least one credential type is selected
     */
    public boolean revokesAnything() {
        return passkeys || sshKeys || vcsAccessTokens;
    }
}
