export interface AdminPasskeyDTO {
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

    /**
     * The ID of the user who owns this passkey
     */
    userId: number;

    /**
     * The login/username of the user who owns this passkey
     */
    userLogin: string;

    /**
     * The full name of the user who owns this passkey
     */
    userName: string;
}
