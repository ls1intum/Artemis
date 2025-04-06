export type PasskeyDto = {
    credentialId: string;
    signatureCount: number;
    created: string; // ISO 8601 date string
    lastUsed: string; // ISO 8601 date string
};
