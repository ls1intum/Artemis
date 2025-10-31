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

const InvalidStateError = {
    name: 'InvalidStateError',
    authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode: 11,
};

const UserAbortedPasskeyCreationError = {
    code: 0,
    name: 'NotAllowedError',
};

function handleMalformedBitwardenCredential(credential: Credential | null) {
    try {
        const malformedBitwardenCredential: MalformedBitwardenCredential = credential as unknown as MalformedBitwardenCredential;
        return getCredentialFromMalformedBitwardenObject(malformedBitwardenCredential);
    } catch (error) {
        throw new InvalidCredentialError();
    }
}

function handleMalformed1Password8Credential(credential: Credential | null) {
    try {
        const malformed1Password8Credential: Malformed1Password8Credential = credential as unknown as Malformed1Password8Credential;
        return getCredentialFromMalformed1Password8Object(malformed1Password8Credential);
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
export function getCredentialWithGracefullyHandlingAuthenticatorIssues(credential: Credential | null) {
    try {
        // properly returned credentials can be stringified
        JSON.stringify(credential);
        return credential;
    } catch (error) {
        captureException(error);
        // eslint-disable-next-line no-undef
        console.warn('Authenticator returned a malformed credential, attempting to fix it', error);

        // Authenticators, such as bitwarden, do not handle the credential generation properly; this is a workaround for it
        let fixedCredential = handleMalformedBitwardenCredential(credential);

        const is1Password8Credential = (fixedCredential as any).response?.authenticatorData === '';
        if (is1Password8Credential) {
            // eslint-disable-next-line no-undef
            console.warn('Bitwarden workaround did not succeed, attempting 1password8 workaround', error);
            fixedCredential = handleMalformed1Password8Credential(credential);
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
        if (!credential) {
            // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
            throw new InvalidCredentialError();
        }

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
