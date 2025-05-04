import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { getCredentialFromMalformedBitwardenObject } from 'app/core/user/settings/passkey-settings/util/bitwarden.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';

function handleMalformedBitwardenCredential(credential: Credential | null) {
    try {
        const malformedBitwardenCredential: MalformedBitwardenCredential = credential as unknown as MalformedBitwardenCredential;
        return getCredentialFromMalformedBitwardenObject(malformedBitwardenCredential);
    } catch (error) {
        throw new InvalidCredentialError();
    }
}

/**
 * <p>Handles credentials gracefully by attempting to stringify them. If the credential cannot
 * be stringified (e.g., due to issues with certain authenticators like Bitwarden), it attempts
 * a workaround to convert the malformed credential into a valid format. This workaround was introduced
 * for Bitwarden and might fail for other authenticators.
 * </p>
 *
 * <p><strong>Authenticators that return a proper {@link Credential} are not affected by this workaround!</strong></p>
 *
 * @throws {@link InvalidCredentialError} if the credential cannot be processed.
 */
export function getCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null) {
    try {
        // properly returned credentials can be stringified
        JSON.stringify(credential);
        return credential;
    } catch (error) {
        // eslint-disable-next-line no-undef
        console.warn('Authenticator returned a malformed credential, attempting to fix it', error);
        // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
        handleMalformedBitwardenCredential(credential);
    }
}
