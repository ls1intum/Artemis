import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { CreatePasskeyDTO } from 'app/shared/user-settings/passkey-settings/dto/create-passkey.dto';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `core/public/webauthn`;

    async createNewPasskey(createPasskeyDto: CreatePasskeyDTO): Promise<void> {
        return await this.post<void>(`${this.basePath}/signup`, createPasskeyDto);
    }

    async loginWithPasskey(credential: Credential) {
        return await this.post<void>(`${this.basePath}/authenticate`, credential);
    }
}
