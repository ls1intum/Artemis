import { AlertService } from 'app/shared/service/alert.service';
import { User } from 'app/core/user/user.model';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { getOS } from 'app/shared/util/os-detector.util';
import { createCredentialOptions } from 'app/core/user/settings/passkey-settings/util/credential-option.util';

const InvalidStateError = {
    name: 'InvalidStateError',
    authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode: 11,
};

const UserAbortedPasskeyCreationError = {
    code: 0,
    name: 'NotAllowedError',
};

export async function addNewPasskey(user: User | undefined, webauthnApiService: WebauthnApiService, alertService: AlertService) {
    try {
        if (!user) {
            // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
            throw new Error('User or Username is not defined');
        }
        const options = await webauthnApiService.getRegistrationOptions();

        const credentialOptions = createCredentialOptions(options, user);
        const credential = await navigator.credentials.create({
            publicKey: credentialOptions,
        });

        await webauthnApiService.registerPasskey({
            publicKey: {
                credential: credential,
                label: `${user.email} - ${getOS()}`,
            },
        });
    } catch (error) {
        if (error.name == UserAbortedPasskeyCreationError.name && error.code == UserAbortedPasskeyCreationError.code) {
            return; // the user pressed cancel in the passkey creation dialog
        }

        if (error.name == InvalidStateError.name && error.code == InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode) {
            alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
        } else {
            alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.registration');
        }
        return;
    }
}
