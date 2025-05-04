export interface MalformedBitwardenCredential {
    id: string;
    rawId: Record<string, number>;
    type: string;
    authenticatorAttachment: string;
    response: {
        clientDataJSON: Record<string, number>;
        attestationObject?: Record<string, number>; // only available on register, not on login
        getAuthenticatorData?: () => Record<string, number>; // only available on register, not on login
        authenticatorData: Record<string, number>; // only available on login, not on register
        getPublicKey: () => Record<string, number>; // only available on register, not on login
        getPublicKeyAlgorithm: () => number; // only available on register, not on login
        getTransports: () => string[]; // only available on register, not on login
        signature?: Record<string, number>; // only available on login, not on register
        userHandle?: Record<string, number>; // only available on login, not on register
    };
    getClientExtensionResults: () => unknown;
}
