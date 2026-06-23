/**
 * <a href="https://chromewebstore.google.com/detail/nngceckbapebfimnlniiiahkandclblb?utm_source=item-share-cb">Bitwarden Browser Plugin</a>
 * does not return a valid Credential object on Chrome https://github.com/bitwarden/clients/issues/12060
 */
export interface MalformedBitwardenRegistrationCredential {
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
