export interface RegisterPasskeyDTO {
    publicKey: {
        /** should always be defined, otherwise the server won't accept the passkey registration */
        credential: Credential | null;
        label: string;
    };
}
