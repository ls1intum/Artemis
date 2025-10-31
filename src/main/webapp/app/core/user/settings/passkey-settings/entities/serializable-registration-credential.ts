/**
 * Represents a serializable version of a PublicKeyCredential for registration
 * with all binary data converted to base64url-encoded strings for JSON serialization.
 *
 * This type is used when converting malformed registration credentials from authenticators
 * (like Bitwarden or 1Password8) that don't properly implement toJSON().
 */
export interface SerializableRegistrationCredential {
    /**
     * The type of credential, typically 'public-key'
     */
    type: PublicKeyCredentialType;

    id: string;

    /**
     * The raw credential ID as a base64url-encoded string
     */
    rawId: string | undefined;

    authenticatorAttachment: AuthenticatorAttachment | undefined;

    clientExtensionResults: AuthenticationExtensionsClientOutputs;

    /**
     * The authenticator response for registration with all binary data encoded as base64url strings
     */
    response: SerializableRegistrationResponse;
}

/**
 * Serializable version of AuthenticatorAttestationResponse (registration response)
 * with all binary data converted to base64url-encoded strings.
 */
export interface SerializableRegistrationResponse {
    /**
     * Client data JSON as base64url string
     */
    clientDataJSON: string | undefined;

    /**
     * Attestation object as base64url string (registration only)
     */
    attestationObject?: string;

    /**
     * Authenticator data as base64url string
     */
    authenticatorData?: string;

    /**
     * Public key as base64url string (registration only)
     */
    publicKey?: string;

    /**
     * Public key algorithm identifier (registration only)
     */
    publicKeyAlgorithm?: number;

    /**
     * Supported transports (registration only)
     */
    transports?: string[];
}
