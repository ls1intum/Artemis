import { Injectable } from '@angular/core';
import { PasskeyOptions } from 'app/shared/user-settings/passkey-settings/entities/passkey-options.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class PasskeySettingsApiService extends BaseApiHttpService {
    private readonly basePath = `webauthn`;

    async getWebauthnOptions(): Promise<PasskeyOptions> {
        return await this.get<PasskeyOptions>(`${this.basePath}/attestation/options`);
    }

    async createNewPasskey(credential: Credential): Promise<void> {
        return await this.post<void>(`${this.basePath}/attestation/result`, credential);
    }
}
