// TODO define the type better by re-using library types?

export interface AssertionServerOptions {
    challenge: string;
    timeout?: number;
    rpId?: string;
    allowCredentials?: WebAuthnPublicKeyCredentialDescriptor[];
    userVerification?: UserVerificationRequirement;
    extensions?: AuthenticationExtensionsClientInputs;
}

export interface WebAuthnPublicKeyCredentialDescriptor {
    type: PublicKeyCredentialType;
    id: string;
    transports?: AuthenticatorTransport[];
}
