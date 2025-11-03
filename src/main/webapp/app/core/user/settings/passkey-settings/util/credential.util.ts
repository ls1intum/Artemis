import { MalformedBitwardenRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-registration-credential';
import {
    getLoginCredentialFromMalformedBitwardenObject,
    getRegistrationCredentialFromMalformedBitwardenObject,
} from 'app/core/user/settings/passkey-settings/util/bitwarden/bitwarden.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/errors/invalid-credential.error';
import { captureException } from '@sentry/angular';
import {
    getLoginCredentialFromMalformed1Password8Object,
    getRegistrationCredentialFromMalformed1Password8Object,
} from 'app/core/user/settings/passkey-settings/util/1password8/1password8.util';
import { Malformed1password8RegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-registration-credential';
import { SerializableRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-registration-credential';
import { SerializableLoginCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-login-credential';
import { MalformedBitwardenLoginCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-login-credential';
import { Malformed1Password8LoginCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-login-credential';

/**
 * Generic handler for converting malformed registration credentials to serializable credentials.
 *
 * @param credential - to convert
 * @param converterFunction - to use for conversion
 * @returns The converted serializable registration credential
 * @throws InvalidCredentialError if the conversion fails
 */
function handleMalformedRegistrationCredential<T>(
    credential: Credential | null,
    converterFunction: (credential: T) => SerializableRegistrationCredential | undefined,
): SerializableRegistrationCredential {
    try {
        const malformedCredential = credential as unknown as T;
        const serializableCredential = converterFunction(malformedCredential);
        if (!serializableCredential) {
            // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
            throw new InvalidCredentialError();
        }
        return serializableCredential;
    } catch (error) {
        throw new InvalidCredentialError();
    }
}

/**
 * Generic handler for converting malformed login credentials to serializable credentials.
 *
 * @param credential - to convert
 * @param converterFunction - to use for conversion
 * @returns The converted serializable login credential
 * @throws InvalidCredentialError if the conversion fails
 */
function handleMalformedLoginCredential<T>(
    credential: Credential | null,
    converterFunction: (credential: T) => SerializableLoginCredential | undefined,
): SerializableLoginCredential {
    try {
        const malformedCredential = credential as unknown as T;
        const serializableCredential = converterFunction(malformedCredential);
        if (!serializableCredential) {
            // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
            throw new InvalidCredentialError();
        }
        return serializableCredential;
    } catch (error) {
        throw new InvalidCredentialError();
    }
}

/**
 * Generic handler for processing credentials gracefully with authenticator-specific workarounds.
 *
 * @param credential - The credential to process
 * @param credentialType - for logging purposes ('registration' or 'login')
 * @param bitwardenConverter - for Bitwarden credentials (the converted is different for registration and login)
 * @param onePassword8Converter - for 1Password8 credentials (the converted is different for registration and login)
 * @param malformedHandler - Handler function for converting malformed credentials
 * @returns The processed credential
 * @throws {@link InvalidCredentialError} if the credential cannot be processed.
 */
function getCredentialWithGracefullyHandlingAuthenticatorIssues<T extends SerializableRegistrationCredential | SerializableLoginCredential>(
    credential: Credential | null,
    credentialType: 'registration' | 'login',
    bitwardenConverter: (malformedBitwardenCredential: MalformedBitwardenRegistrationCredential | MalformedBitwardenLoginCredential | null) => T | undefined,
    onePassword8Converter: (malformed1Password8Credential: Malformed1password8RegistrationCredential | Malformed1Password8LoginCredential | null) => T | undefined,
    malformedHandler: <U>(credential: Credential | null, converterFunction: (credential: U) => T | undefined) => T,
): Credential | T {
    try {
        // properly returned credentials can be stringified
        JSON.stringify(credential);

        if (!credential) {
            // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
            throw new InvalidCredentialError();
        }
        return credential;
    } catch (error) {
        captureException(error);
        // eslint-disable-next-line no-undef
        console.warn(`Authenticator returned a malformed ${credentialType} credential, attempting to fix it`, error);

        // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
        let fixedCredential = malformedHandler<MalformedBitwardenRegistrationCredential>(credential, bitwardenConverter);

        // 1Password8 returns empty string for authenticatorData when the Bitwarden workaround is applied
        const is1Password8Credential = fixedCredential.response?.authenticatorData === '';
        if (is1Password8Credential) {
            // eslint-disable-next-line no-undef
            console.warn('Bitwarden workaround did not succeed, attempting 1password8 workaround', error);
            fixedCredential = malformedHandler<Malformed1password8RegistrationCredential>(credential, onePassword8Converter);
        }

        return fixedCredential;
    }
}

/**
 * <p>Handles registration credentials gracefully by attempting to stringify them. If the credential cannot
 * be stringified (e.g., due to issues with certain authenticators like Bitwarden), it attempts
 * a workaround to convert the malformed credential into a valid format. This workaround was introduced
 * for Bitwarden and might fail for other authenticators.
 * </p>
 *
 * <p><strong>Authenticators that return a properly serializable {@link Credential} are not affected by this workaround!</strong></p>
 *
 * @param credential - The registration credential to process
 * @throws {@link InvalidCredentialError} if the credential cannot be processed.
 */
export function getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null): Credential | SerializableRegistrationCredential {
    return getCredentialWithGracefullyHandlingAuthenticatorIssues<SerializableRegistrationCredential>(
        credential,
        'registration',
        getRegistrationCredentialFromMalformedBitwardenObject,
        getRegistrationCredentialFromMalformed1Password8Object,
        handleMalformedRegistrationCredential,
    );
}

/**
 * <p>Handles login credentials gracefully by attempting to stringify them. If the credential cannot
 * be stringified (e.g., due to issues with certain authenticators like Bitwarden), it attempts
 * a workaround to convert the malformed credential into a valid format. This workaround was introduced
 * for Bitwarden and might fail for other authenticators.
 * </p>
 *
 * <p><strong>Authenticators that return a proper {@link Credential} are not affected by this workaround!</strong></p>
 *
 * @param credential - The login credential to process
 * @throws {@link InvalidCredentialError} if the credential cannot be processed.
 */
export function getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null): Credential | SerializableLoginCredential {
    return getCredentialWithGracefullyHandlingAuthenticatorIssues<SerializableLoginCredential>(
        credential,
        'login',
        getLoginCredentialFromMalformedBitwardenObject,
        getLoginCredentialFromMalformed1Password8Object,
        handleMalformedLoginCredential,
    );
}
