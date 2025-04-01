/**
 * @see {@link https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html#passkeys-register-create}
 */
export type RegisterPasskeyDto = {
    publicKey: {
        credential: Credential;
        label: string;
    };
};
