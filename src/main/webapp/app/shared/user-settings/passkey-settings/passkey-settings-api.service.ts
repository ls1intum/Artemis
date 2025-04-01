import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { RegisterPasskeyDto } from 'app/shared/user-settings/passkey-settings/dto/register-passkey.dto';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `core/public/webauthn`;

    // TODO add delete and rename endpoint

    async createNewPasskey(registerPasskeyDto: RegisterPasskeyDto): Promise<void> {
        return await this.post<void>(`${this.basePath}/signup`, registerPasskeyDto);
    }
}
