/**
 * Represents a serializable version of a PublicKeyCredential for login
 * with all binary data converted to base64url-encoded strings for JSON serialization.
 *
 * This type is used when converting malformed login credentials from authenticators
 * (like Bitwarden or 1Password8) that don't properly implement `toJSON()`.
 */
export interface SerializableLoginCredential {
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
     * The authenticator response for login with all binary data encoded as base64url strings
     */
    response: SerializableLoginResponse;
}

/**
 * Serializable version of {@link AuthenticatorAssertionResponse}
 * with all binary data converted to base64url-encoded strings.
 */
export interface SerializableLoginResponse {
    /**
     * Client data JSON as base64url string
     */
    clientDataJSON: string | undefined;

    /**
     * Authenticator data as base64url string
     */
    authenticatorData?: string;

    /**
     * Signature as base64url string
     */
    signature?: string;

    /**
     * User handle as base64url string
     */
    userHandle?: string;
}
