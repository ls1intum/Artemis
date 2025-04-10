export interface PasskeyDto {
    /**
     * The credential ID of the passkey, encoded in Base64url format.
     */
    credentialId: string;
    label: string;

    /**
     * ISO 8601 date string
     */
    created: string;

    /**
     * ISO 8601 date string
     */
    lastUsed: string;
}
