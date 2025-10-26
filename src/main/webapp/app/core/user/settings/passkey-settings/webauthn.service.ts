import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { getCredentialWithGracefullyHandlingAuthenticatorIssues } from 'app/core/user/settings/passkey-settings/util/credential.util';
import { AlertService } from 'app/shared/service/alert.service';

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private readonly alertService = inject(AlertService);
    private readonly webauthnApiService = inject(WebauthnApiService);

    private async getCredential(): Promise<PublicKeyCredential | undefined> {
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

    async loginWithPasskey() {
        try {
            const authenticatorCredential = await this.getCredential();

            if (!authenticatorCredential || authenticatorCredential.type != 'public-key') {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            const credential = getCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential) as unknown as PublicKeyCredential;
            if (!credential) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            await this.webauthnApiService.loginWithPasskey(credential);
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
