import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/shared/user-settings/passkey-settings/webauthn-api.service';
import { base64UrlDecode } from 'app/shared/util/utils';
import { WebAuthn4NgCredentialRequestOptions } from 'app/shared/user-settings/passkey-settings/passkey-settings.component';

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private webauthnApiService = inject(WebauthnApiService);

    async getCredential(publicKeyCredentialRequestOptions: WebAuthn4NgCredentialRequestOptions): Promise<Credential | undefined> {
        const serverAssertionOptions = await this.webauthnApiService.getAssertionOptions();

        const assertionOptions: PublicKeyCredentialRequestOptions = {
            challenge: base64UrlDecode(serverAssertionOptions.challenge),
            timeout: serverAssertionOptions.timeout,
            rpId: serverAssertionOptions.rpId,
            allowCredentials: serverAssertionOptions.allowCredentials
                ? serverAssertionOptions.allowCredentials.map((credential) => {
                      return {
                          type: credential.type,
                          id: base64UrlDecode(credential.id),
                          transports: credential.transports,
                      };
                  })
                : undefined,
            userVerification: serverAssertionOptions.userVerification,
            extensions: serverAssertionOptions.extensions,
        };

        const mergedOptions = { ...assertionOptions, ...publicKeyCredentialRequestOptions };
        const credentialRequestOptions: CredentialRequestOptions = {
            publicKey: mergedOptions,
        };

        return (await navigator.credentials.get(credentialRequestOptions)) ?? undefined;
    }
}
