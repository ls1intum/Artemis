import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { getCredentialFromInvalidBitwardenObject } from 'app/core/user/settings/passkey-settings/util/bitwarden.util';

export function getCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null) {
    try {
        // properly returned credentials can be stringified
        JSON.stringify(credential);
        return credential;
    } catch (error) {
        // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
        const malformedBitwardenCredential: MalformedBitwardenCredential = credential as unknown as MalformedBitwardenCredential;
        return getCredentialFromInvalidBitwardenObject(malformedBitwardenCredential);
    }
}
