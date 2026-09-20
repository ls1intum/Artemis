export interface LoginOptionsDTO {
    loginMethod: 'PASSWORD' | 'OIDC' | 'SAML2';
    idpName: string | null;
}
