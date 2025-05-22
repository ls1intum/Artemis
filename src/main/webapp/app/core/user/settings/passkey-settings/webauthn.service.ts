import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { PasskeyAbortError } from 'app/core/user/settings/passkey-settings/entities/passkey-abort-error';

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private webauthnApiService = inject(WebauthnApiService);

    private authAbortController = new AbortController();

    /**
     * To support passkey autocomplete, we need to have a pending getCredential request with conditional mediation.
     * If we tried to use the "Sign in with Passkey" button, without aborting the pending conditional request,
     * we would get an error as we cannot have multiple credential requests at the same time.
     */
    private ensureAtMostOneCredentialRequestIsActive() {
        try {
            this.authAbortController.abort(new PasskeyAbortError());
        } catch (error) {
            // we can ignore the error if it's the expected abort error
            if (!(error instanceof PasskeyAbortError)) {
                throw error;
            }
        }
        this.authAbortController = new AbortController();
    }

    /**
     * Retrieves a credential from the client, according to the options provided by the server.
     *
     * @param isConditional true if credential shall be requested with conditional mediation
     * @returns the credential or undefined if no credential was selected or retrievable
     */
    async getCredential(isConditional: boolean): Promise<PublicKeyCredential | undefined> {
        this.ensureAtMostOneCredentialRequestIsActive();

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
