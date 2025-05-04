import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { getCredentialFromMalformedBitwardenObject } from 'app/core/user/settings/passkey-settings/util/bitwarden.util';

/**
 * <p>Handles credentials gracefully by attempting to stringify them. If the credential cannot
 * be stringified (e.g., due to issues with certain authenticators like Bitwarden), it attempts
 * a workaround to convert the malformed credential into a valid format. This workaround was introduced
 * for Bitwarden and might fail for other authenticators.
 * </p>
 *
 * <p><strong>Authenticators that return a proper {@link Credential} are not affected by this workaround!</strong></p>
 */
export function getCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null) {
    try {
        // properly returned credentials can be stringified
        JSON.stringify(credential);
        return credential;
    } catch (error) {
        // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
        const malformedBitwardenCredential: MalformedBitwardenCredential = credential as unknown as MalformedBitwardenCredential;
        return getCredentialFromMalformedBitwardenObject(malformedBitwardenCredential);
    }
}
