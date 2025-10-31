/**
 * Represents a serializable version of a PublicKeyCredential with all binary data
 * converted to base64url-encoded strings for JSON serialization.
 *
 * This type is used when converting malformed credentials from authenticators
 * (like Bitwarden or 1Password8) that don't properly implement toJSON().
 */
export interface SerializableCredential {
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
     * The authenticator response with all binary data encoded as base64url strings
     */
    response: SerializableAuthenticatorResponse;
}

/**
 * Serializable version of AuthenticatorResponse with all binary data
 * converted to base64url-encoded strings.
 *
 * This combines properties from both {@link AuthenticatorAttestationResponse} (registration)
 * and {@link AuthenticatorAssertionResponse} (login).
 */
export interface SerializableAuthenticatorResponse {
    /**
     * Client data JSON as base64url string
     */
    clientDataJSON: string | undefined;

    /**
     * Attestation object as base64url string (registration only)
     */
    attestationObject?: string;

    /**
     * Authenticator data as base64url string (available in both registration and login)
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

    /**
     * Signature as base64url string (login only)
     */
    signature?: string;

    /**
     * User handle as base64url string (login only)
     */
    userHandle?: string;
}
