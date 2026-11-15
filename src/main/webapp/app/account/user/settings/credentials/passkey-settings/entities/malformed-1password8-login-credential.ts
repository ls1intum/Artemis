export interface Malformed1Password8LoginCredential {
    authenticatorAttachment: string;
    getClientExtensionResults(): unknown;
    id: string;
    rawId: ArrayBuffer;
    response: {
        clientDataJSON: ArrayBuffer;
        getAuthenticatorData?: () => ArrayBuffer;
        authenticatorData?: ArrayBuffer;
        signature?: ArrayBuffer;
        userHandle?: ArrayBuffer;
    };
    type: string;
}
