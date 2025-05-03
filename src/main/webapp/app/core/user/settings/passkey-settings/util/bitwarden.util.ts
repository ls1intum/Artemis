import cloneDeep from 'lodash';

export type MalformedBitwardenCredential = {
    id: string;
    rawId: Record<string, number>;
    type: string;
    authenticatorAttachment: string;
    response: {
        clientDataJSON: Record<string, number>;
        attestationObject: Record<string, number>;
        getAuthenticatorData: () => Record<string, number>;
        getPublicKey: () => Record<string, number>;
        getPublicKeyAlgorithm: () => number;
        getTransports: () => string[];
    };
    getClientExtensionResults: () => unknown;
}

function convertToArrayBuffer(rawIdObject: Record<string, number> | null | undefined): ArrayBuffer {
    if (!rawIdObject || typeof rawIdObject !== 'object') {
        throw new TypeError('Invalid input: rawIdObject must be a non-null object');
    }

    const uint8Array = new Uint8Array(Object.values(rawIdObject));
    return uint8Array.buffer;
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
    const uint8Array = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < uint8Array.length; i++) {
        binary += String.fromCharCode(uint8Array[i]);
    }
    return btoa(binary).replace(/\//g, '_').replace(/\+/g, '-');
}

export function getCredentialFromInvalidBitwardenObject(malformedBitwardenCredential: MalformedBitwardenCredential): Credential | null {
    if (!malformedBitwardenCredential) {
        return null;
    }

    /**
     * By cloning the object with lodash, we have an easy way to get a version that can be stringified
     */
    let clonedCredential: Credential = cloneDeep(malformedBitwardenCredential) as unknown as Credential;

    const serializedCredential = JSON.stringify(clonedCredential);
    const credential = JSON.parse(serializedCredential);

    const rawIdAsArrayBuffer = convertToArrayBuffer(credential.rawId);
    const clientDataJSONAsArrayBuffer = convertToArrayBuffer(credential.response.clientDataJSON);
    const attestationObjectAsArrayBuffer = convertToArrayBuffer(credential.response.attestationObject);

    return {
        //@ts-expect-error authenticatorAttachment is a method in the (getAuthenticatorAttachment) object, but we return it as property here for simplicity, assuming that we only want to stringify it afterward anyways
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
        type: credential.type
    };
}
