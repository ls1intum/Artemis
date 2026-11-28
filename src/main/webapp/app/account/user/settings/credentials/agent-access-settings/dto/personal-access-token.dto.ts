/** A personal access token as listed in the settings. The secret is never part of it. */
export interface PersonalAccessTokenDTO {
    id: number;
    name: string;
    /** The first characters of the secret, to tell tokens apart. */
    tokenPrefix: string;
    scopes: string[];
    createdDate: string;
    expiryDate: string;
    lastUsedDate?: string;
}

export interface CreatePersonalAccessTokenDTO {
    name: string;
    scopes: string[];
    expiresInDays?: number;
}

/** The response to creating a token. The secret is available only in this response. */
export interface CreatedPersonalAccessTokenDTO {
    token: PersonalAccessTokenDTO;
    secret: string;
}
