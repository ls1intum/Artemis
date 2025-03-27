export type CreatePasskeyDTO = {
    userHandle: string;
    webAuthnCredential: Credential;
    username: string;
};
