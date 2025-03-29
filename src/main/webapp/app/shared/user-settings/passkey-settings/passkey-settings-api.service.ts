import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { CreatePasskeyDTO } from 'app/shared/user-settings/passkey-settings/dto/create-passkey.dto';
import { encodeBase64url } from 'app/shared/util/utils';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `core/public/webauthn`;

    async createNewPasskey(createPasskeyDto: CreatePasskeyDTO): Promise<void> {
        return await this.post<void>(`${this.basePath}/signup`, createPasskeyDto);
    }

    // async loginWithPasskey(credential: Credential) {
    //     return await this.post<void>(`${this.basePath}/authenticate`, credential);
    // }

    async loginWithPasskey(credential: Credential) {
        if (credential.type != 'public-key') {
            alert('Unexpected credential type');
        }
        const publicKeyCredential: PublicKeyCredential = credential as PublicKeyCredential;
        const assertionResponse: AuthenticatorAssertionResponse = publicKeyCredential.response as AuthenticatorAssertionResponse;
        const clientDataJSON = assertionResponse.clientDataJSON;
        const authenticatorData = assertionResponse.authenticatorData;
        const signature = assertionResponse.signature;
        const clientExtensions = publicKeyCredential.getClientExtensionResults();

        const formData = new FormData();
        formData.set('credentialId', encodeBase64url(new Uint8Array(publicKeyCredential.rawId)));
        formData.set('clientDataJSON', encodeBase64url(new Uint8Array(clientDataJSON)));
        formData.set('authenticatorData', encodeBase64url(new Uint8Array(authenticatorData)));
        formData.set('signature', encodeBase64url(new Uint8Array(signature)));
        formData.set('clientExtensionsJSON', JSON.stringify(clientExtensions));

        return await this.post<void>(`${this.basePath}/authenticate`, formData, { responseType: 'text' });
    }
}
