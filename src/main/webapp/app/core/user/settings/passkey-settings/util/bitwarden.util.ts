import cloneDeep from 'lodash';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';

function convertToBase64(rawObject: Record<string, number> | null | undefined): string {
    if (!rawObject || typeof rawObject !== 'object') {
        throw new TypeError('Invalid input: must be a non-null object');
    }
    const uint8Array = new Uint8Array(Object.values(rawObject));
    // eslint-disable-next-line no-undef
    return btoa(String.fromCharCode(...uint8Array))
        .replace(/\//g, '_')
        .replace(/\+/g, '-');
}

export function getCredentialFromInvalidBitwardenObject(malformedBitwardenCredential: MalformedBitwardenCredential | null): Credential | null {
    if (!malformedBitwardenCredential) {
        return null;
    }

    const clonedCredential: Credential = cloneDeep(malformedBitwardenCredential) as unknown as Credential;
    const credential = JSON.parse(JSON.stringify(clonedCredential));

    return {
        //@ts-expect-error authenticatorAttachment is a method in the (getAuthenticatorAttachment) object, but we return it as property here for simplicity, assuming that we only want to stringify it afterward anyway
        authenticatorAttachment: credential.authenticatorAttachment,
        clientExtensionResults: malformedBitwardenCredential.getClientExtensionResults(),
        id: credential.id,
        rawId: convertToBase64(credential.rawId),
        response: {
            attestationObject: convertToBase64(credential.response.attestationObject),
            authenticatorData: convertToBase64(malformedBitwardenCredential.response.getAuthenticatorData()),
            clientDataJSON: convertToBase64(credential.response.clientDataJSON),
            publicKey: convertToBase64(malformedBitwardenCredential.response.getPublicKey()),
            publicKeyAlgorithm: malformedBitwardenCredential.response.getPublicKeyAlgorithm(),
            transports: malformedBitwardenCredential.response.getTransports(),
        },
        type: credential.type,
    };
}
