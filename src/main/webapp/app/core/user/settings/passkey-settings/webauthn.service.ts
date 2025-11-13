import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/errors/invalid-credential.error';
import {
    getLoginCredentialWithGracefullyHandlingAuthenticatorIssues,
    getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues,
} from 'app/core/user/settings/passkey-settings/util/credential.util';
import { AlertService } from 'app/shared/service/alert.service';
import { User } from 'app/core/user/user.model';
import { createCredentialOptions } from 'app/core/user/settings/passkey-settings/util/credential-option.util';
import { getOS } from 'app/shared/util/os-detector.util';
import { UserAbortedPasskeyCreationError } from 'app/core/user/settings/passkey-settings/entities/errors/user-aborted-passkey-creation.error';
import { InvalidStateError } from 'app/core/user/settings/passkey-settings/entities/errors/invalid-state.error';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private readonly alertService = inject(AlertService);
    private readonly accountService = inject(AccountService);
    private readonly webauthnApiService = inject(WebauthnApiService);

    async getCredential(): Promise<PublicKeyCredential | undefined> {
        const publicKeyCredentialOptions = await this.webauthnApiService.getAuthenticationOptions();

        const assertionOptions: PublicKeyCredentialRequestOptions = {
            challenge: decodeBase64url(publicKeyCredentialOptions.challenge),
            timeout: publicKeyCredentialOptions.timeout,
            rpId: publicKeyCredentialOptions.rpId,
            allowCredentials: publicKeyCredentialOptions.allowCredentials
                ? publicKeyCredentialOptions.allowCredentials.map((credential) => {
                      return {
                          type: credential.type,
                          id: decodeBase64url(credential.id),
                          transports: credential.transports,
                      };
                  })
                : undefined,
            userVerification: publicKeyCredentialOptions.userVerification,
            extensions: publicKeyCredentialOptions.extensions,
        };

        const credentialRequestOptions: CredentialRequestOptions = {
            publicKey: assertionOptions,
        };

        const credential = (await navigator.credentials.get(credentialRequestOptions)) ?? undefined;
        return credential as PublicKeyCredential | undefined;
    }

    async addNewPasskey(user: User | undefined) {
        try {
            if (!user) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new Error('User or Username is not defined');
            }
            const registrationOptions = await this.webauthnApiService.getRegistrationOptions();
            const credentialOptions = createCredentialOptions(registrationOptions, user);

            const authenticatorCredential = await navigator.credentials.create({
                publicKey: credentialOptions,
            });
            const credential = getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential ?? undefined);

            await this.webauthnApiService.registerPasskey({
                publicKey: {
                    credential: credential,
                    label: `${user.email} - ${getOS()}`,
                },
            });
        } catch (error) {
            const userPressedCancelInPasskeyCreationDialog = error.name === UserAbortedPasskeyCreationError.name && error.code === UserAbortedPasskeyCreationError.code;
            if (userPressedCancelInPasskeyCreationDialog) {
                return;
            }

            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else if (error.name === InvalidStateError.name && error.code === InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.registration');
            }

            throw error;
        }
    }

    /**
     * Logs in a user using passkey authentication.
     *
     * @throws InvalidCredentialError if the credential is invalid
     * @throws Error for other authentication errors
     */
    async loginWithPasskey() {
        try {
            const authenticatorCredential = await this.getCredential();

            if (!authenticatorCredential || authenticatorCredential.type !== 'public-key') {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            const credential = getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential) as unknown as PublicKeyCredential;
            if (!credential) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            await this.webauthnApiService.loginWithPasskey(credential);

            this.accountService.userIdentity.set({
                ...this.accountService.userIdentity(),
                isLoggedInWithPasskey: true,
                internal: this.accountService.userIdentity()?.internal ?? false,
            });
        } catch (error) {
            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.login');
            }
            // eslint-disable-next-line no-undef
            console.error(error);
            throw error;
        }
    }
}
