import { Injectable } from '@angular/core';
import { PasskeyOptions } from 'app/shared/user-settings/passkey-settings/entities/passkey-options.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { CreatePasskeyDTO } from 'app/shared/user-settings/passkey-settings/dto/create-passkey.dto';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `core/public/webauthn`;

    /**
     * Note: WebAuthn4j exposes the `/webauthn/attestation/options` endpoint, the endpoint is not explicitly defined in a resource
     */
    async getWebauthnOptions(): Promise<PasskeyOptions> {
        return await this.get<PasskeyOptions>(`webauthn/attestation/options`);
    }

    /**
     * Note: WebAuthn4j exposes the `/webauthn/assertion/options` endpoint, the endpoint is not explicitly defined in a resource
     */
    async getAssertionOptions(): Promise<PublicKeyCredentialRequestOptionsJSON> {
        return await this.get<PublicKeyCredentialRequestOptionsJSON>(`webauthn/assertion/options`);
    }

    // async createNewPasskey(createPasskeyDTO: CreatePasskeyDTO): Promise<void> {
    //     return await this.post<void>(`${this.basePath}/signup`, createPasskeyDTO);
    // }

    async createNewPasskey(credential: CreatePasskeyDTO): Promise<void> {
        return await this.post<void>(`${this.basePath}/signup`, credential);
    }
}
