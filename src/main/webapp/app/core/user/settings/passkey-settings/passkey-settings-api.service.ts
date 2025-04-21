import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `core/passkey`;

    async getRegisteredPasskeys(): Promise<PasskeyDTO[]> {
        return await this.get<PasskeyDTO[]>(`${this.basePath}/user`);
    }

    async deletePasskey(credentialId: string): Promise<void> {
        return await this.delete(`${this.basePath}/${credentialId}`);
    }
}
