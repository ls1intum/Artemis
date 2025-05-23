import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/base64.util';

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private webauthnApiService = inject(WebauthnApiService);

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
}
