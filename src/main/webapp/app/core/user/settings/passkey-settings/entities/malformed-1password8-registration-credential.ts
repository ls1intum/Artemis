export interface Malformed1password8RegistrationCredential {
    authenticatorAttachment: string;
    getClientExtensionResults(): unknown;
    id: string;
    rawId: ArrayBuffer;
    response: {
        attestationObject: ArrayBuffer;
        clientDataJSON: ArrayBuffer;
        getAuthenticatorData: () => ArrayBuffer;
        getPublicKey: () => ArrayBuffer;
        getPublicKeyAlgorithm: () => number;
        getTransports: () => string[];
    };
    type: string;
}
