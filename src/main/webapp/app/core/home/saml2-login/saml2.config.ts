// NOTE: Those values are specified in application-saml2.yml and automatically mapped to the below Typescript attributes when the saml2 profile is active. Admins can override the values in application-prod.yml
export interface Saml2Config {
    identityProviderName?: string;
    buttonLabel?: string;
    passwordLoginDisabled: boolean;
    enablePassword: boolean;
}
