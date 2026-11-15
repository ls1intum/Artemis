export interface PasskeyDTO {
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

    /**
     * Super admin approval is required for privileged features (e.g. administrator features)
     */
    isSuperAdminApproved: boolean;
}
