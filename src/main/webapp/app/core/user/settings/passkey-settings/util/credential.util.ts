import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { getCredentialFromMalformedBitwardenObject } from 'app/core/user/settings/passkey-settings/util/bitwarden/bitwarden.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { captureException } from '@sentry/angular';
import { AlertService } from 'app/shared/service/alert.service';
import { User } from 'app/core/user/user.model';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { getOS } from 'app/shared/util/os-detector.util';
import { createCredentialOptions } from 'app/core/user/settings/passkey-settings/util/credential-option.util';
import { getCredentialFromMalformed1Password8Object } from 'app/core/user/settings/passkey-settings/util/1password8/1password8.util';
import { Malformed1Password8Credential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-credential';
import { SerializableCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-credential';

const InvalidStateError = {
    name: 'InvalidStateError',
    authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode: 11,
};

const UserAbortedPasskeyCreationError = {
    code: 0,
    name: 'NotAllowedError',
};

/**
 * Generic handler for converting malformed credentials to serializable credentials.
 *
 * @param credential - The credential to convert
 * @param converterFunction - The function to use for conversion
 * @returns The converted serializable credential
 * @throws InvalidCredentialError if the conversion fails
 */
function handleMalformedCredential<T>(credential: Credential | null, converterFunction: (credential: T) => SerializableCredential | undefined): SerializableCredential {
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
 * <p>Handles credentials gracefully by attempting to stringify them. If the credential cannot
 * be stringified (e.g., due to issues with certain authenticators like Bitwarden), it attempts
 * a workaround to convert the malformed credential into a valid format. This workaround was introduced
 * for Bitwarden and might fail for other authenticators.
 * </p>
 *
 * <p><strong>Authenticators that return a proper {@link Credential} are not affected by this workaround!</strong></p>
 *
 * @throws {@link InvalidCredentialError} if the credential cannot be processed.
 */
export function getCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null): Credential | SerializableCredential {
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
        console.warn('Authenticator returned a malformed credential, attempting to fix it', error);

        // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
        let fixedCredential = handleMalformedCredential<MalformedBitwardenCredential>(credential, getCredentialFromMalformedBitwardenObject);

        // 1Password8 returns empty string for authenticatorData when the Bitwarden workaround is applied
        const is1Password8Credential = fixedCredential.response?.authenticatorData === '';
        if (is1Password8Credential) {
            // eslint-disable-next-line no-undef
            console.warn('Bitwarden workaround did not succeed, attempting 1password8 workaround', error);
            fixedCredential = handleMalformedCredential<Malformed1Password8Credential>(credential, getCredentialFromMalformed1Password8Object);
        }

        return fixedCredential;
    }
}

export async function addNewPasskey(user: User | undefined, webauthnApiService: WebauthnApiService, alertService: AlertService) {
    try {
        if (!user) {
            // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
            throw new Error('User or Username is not defined');
        }
        const registrationOptions = await webauthnApiService.getRegistrationOptions();
        const credentialOptions = createCredentialOptions(registrationOptions, user);

        const authenticatorCredential = await navigator.credentials.create({
            publicKey: credentialOptions,
        });
        const credential = getCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential);

        await webauthnApiService.registerPasskey({
            publicKey: {
                credential: credential,
                label: `${user.email} - ${getOS()}`,
            },
        });
    } catch (error) {
        const userPressedCancelInPasskeyCreationDialog = error.name == UserAbortedPasskeyCreationError.name && error.code == UserAbortedPasskeyCreationError.code;
        if (userPressedCancelInPasskeyCreationDialog) {
            return;
        }

        if (error instanceof InvalidCredentialError) {
            alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        } else if (error.name == InvalidStateError.name && error.code == InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode) {
            alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
        } else {
            alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.registration');
        }

        throw error;
    }
}
