import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';
import { PasskeyDTO } from 'app/account/user/settings/passkey-settings/dto/passkey.dto';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `account/passkeys`;

    async getRegisteredPasskeys(): Promise<PasskeyDTO[]> {
        return await this.get<PasskeyDTO[]>(`${this.basePath}/user`);
    }

    async updatePasskeyLabel(credentialId: string, updatedPasskey: PasskeyDTO): Promise<PasskeyDTO> {
        return await this.put(`${this.basePath}/${credentialId}`, updatedPasskey);
    }

    async deletePasskey(credentialId: string): Promise<void> {
        return await this.delete(`${this.basePath}/${credentialId}`);
    }
}
