export type PasskeyDto = {
    credentialId: string;
    label: string;
    created: string; // ISO 8601 date string
    lastUsed: string; // ISO 8601 date string
};
