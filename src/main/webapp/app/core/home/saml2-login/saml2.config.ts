// NOTE: Those values are specified in application-saml2.yml and automatically mapped to the below Typescript attributes when the saml2 profile is active. Admins can override the values in application-prod.yml
export class Saml2Config {
    public identityProviderName?: string;
    public buttonLabel?: string;
    public passwordLoginDisabled: boolean;
    public enablePassword: boolean;
}
