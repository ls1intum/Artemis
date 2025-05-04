export interface MalformedBitwardenCredential {
    id: string;
    rawId: Record<string, number>;
    type: string;
    authenticatorAttachment: string;
    response: {
        clientDataJSON: Record<string, number>;
        /**
         * only available during registration.
         */
        attestationObject?: Record<string, number>;

        /**
         * only available during registration.
         */
        getAuthenticatorData?: () => Record<string, number>;

        /**
         * only available during login.
         */
        authenticatorData?: Record<string, number>;

        /**
         * only available during registration.
         */
        getPublicKey?: () => Record<string, number>;

        /**
         * only available during registration.
         */
        getPublicKeyAlgorithm?: () => number;

        /**
         * only available during registration.
         */
        getTransports?: () => string[];

        /**
         * only available during login.
         */
        signature?: Record<string, number>;

        /**
         * only available during login.
         */
        userHandle?: Record<string, number>;
    };
    getClientExtensionResults: () => unknown;
}
