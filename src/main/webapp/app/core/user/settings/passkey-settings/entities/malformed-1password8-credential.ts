export interface Malformed1Password8Credential {
    authenticatorAttachment: string;
    getClientExtensionResults(): unknown;
    id: string;
    rawId: ArrayBuffer;
    response: {
        attestationObject?: ArrayBuffer;
        clientDataJSON: ArrayBuffer;
        getAuthenticatorData?: () => ArrayBuffer;
        getPublicKey?: () => ArrayBuffer;
        getPublicKeyAlgorithm?: () => number;
        getTransports?: () => string[];
        // the following are only included in login credential
        authenticatorData?: ArrayBuffer;
        signature?: ArrayBuffer;
        userHandle?: ArrayBuffer;
    };
    type: string;
}
