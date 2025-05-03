export interface MalformedBitwardenCredential {
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
