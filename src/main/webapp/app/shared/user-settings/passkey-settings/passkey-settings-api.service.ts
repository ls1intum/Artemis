import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { PasskeyDto } from 'app/shared/user-settings/passkey-settings/dto/passkey.dto';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `core/webauthn`;

    // TODO add delete and rename endpoint

    async getRegisteredPasskeys(): Promise<PasskeyDto[]> {
        return await this.get<PasskeyDto[]>(`${this.basePath}/passkeys`);
    }

    async deletePasskey(credentialId: string): Promise<void> {
        return await this.delete(`${this.basePath}/passkeys/${credentialId}`);
    }

    async loginWithPasskey(publicKeyCredential: PublicKeyCredential): Promise<void> {
        return await this.post<void>(`${this.basePath}/authenticate`, publicKeyCredential);
    }
}
