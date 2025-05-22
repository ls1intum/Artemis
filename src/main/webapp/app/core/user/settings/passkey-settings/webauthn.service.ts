import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { ABORT_ERROR_MESSAGE } from 'app/core/home/home.component';

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private webauthnApiService = inject(WebauthnApiService);

    private authAbortController = new AbortController();

    async getCredential(isConditional: boolean): Promise<PublicKeyCredential | undefined> {
        try {
            this.authAbortController.abort(ABORT_ERROR_MESSAGE);
        } catch (error) {
            // we can only have one credential request at a time
            if (error !== ABORT_ERROR_MESSAGE) {
                throw error;
            }
        }

        this.authAbortController = new AbortController();

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
            signal: this.authAbortController.signal,
            ...(isConditional && {
                mediation: 'conditional',
            }),
        };

        const credential = (await navigator.credentials.get(credentialRequestOptions)) ?? undefined;
        return credential as PublicKeyCredential | undefined;
    }
}
