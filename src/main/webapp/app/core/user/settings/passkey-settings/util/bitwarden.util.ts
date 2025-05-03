import cloneDeep from 'lodash';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';

function convertToArrayBuffer(rawObject: Record<string, number> | null | undefined): ArrayBuffer {
    if (!rawObject || typeof rawObject !== 'object') {
        throw new TypeError('Invalid input: must be a non-null object');
    }

    const uint8Array = new Uint8Array(Object.values(rawObject));
    return uint8Array.buffer;
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
    const uint8Array = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < uint8Array.length; i++) {
        binary += String.fromCharCode(uint8Array[i]);
    }
    // eslint-disable-next-line no-undef
    return btoa(binary).replace(/\//g, '_').replace(/\+/g, '-');
}

export function getCredentialFromInvalidBitwardenObject(malformedBitwardenCredential: MalformedBitwardenCredential | null): Credential | null {
    if (!malformedBitwardenCredential) {
        return null;
    }

    /**
     * By cloning the object with lodash, we have an easy way to get a version that can be stringified
     */
    const clonedCredential: Credential = cloneDeep(malformedBitwardenCredential) as unknown as Credential;

    const serializedCredential = JSON.stringify(clonedCredential);
    const credential = JSON.parse(serializedCredential);

    const rawIdAsArrayBuffer = convertToArrayBuffer(credential.rawId);
    const clientDataJSONAsArrayBuffer = convertToArrayBuffer(credential.response.clientDataJSON);
    const attestationObjectAsArrayBuffer = convertToArrayBuffer(credential.response.attestationObject);

    return {
        //@ts-expect-error authenticatorAttachment is a method in the (getAuthenticatorAttachment) object, but we return it as property here for simplicity, assuming that we only want to stringify it afterward anyway
        authenticatorAttachment: credential.authenticatorAttachment,
        clientExtensionResults: malformedBitwardenCredential.getClientExtensionResults(),
        id: credential.id,
        rawId: arrayBufferToBase64(rawIdAsArrayBuffer),
        response: {
            attestationObject: arrayBufferToBase64(attestationObjectAsArrayBuffer),
            authenticatorData: arrayBufferToBase64(convertToArrayBuffer(malformedBitwardenCredential.response.getAuthenticatorData())),
            clientDataJSON: arrayBufferToBase64(clientDataJSONAsArrayBuffer),
            publicKey: arrayBufferToBase64(convertToArrayBuffer(malformedBitwardenCredential.response.getPublicKey())),
            publicKeyAlgorithm: malformedBitwardenCredential.response.getPublicKeyAlgorithm(),
            transports: malformedBitwardenCredential.response.getTransports(),
        },
        type: credential.type,
    };
}
