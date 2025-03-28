import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { PasskeyOptions } from 'app/shared/user-settings/passkey-settings/entities/passkey-options.model';
import { AssertionServerOptions } from 'app/shared/user-settings/passkey-settings/passkey-settings.component';
import { Injectable } from '@angular/core';

/**
 * Note: [WebAuthn4j](https://github.com/webauthn4j/webauthn4j) exposes the endpoints, the endpoints are not explicitly defined in a resource
 */
@Injectable({ providedIn: 'root' })
export class WebauthnApiService extends BaseApiHttpService {
    private readonly basePath = `webauthn`;

    async getWebauthnOptions(): Promise<PasskeyOptions> {
        return await this.get<PasskeyOptions>(`${this.basePath}/attestation/options`);
    }

    async getAssertionOptions(): Promise<AssertionServerOptions> {
        return await this.get<AssertionServerOptions>(`${this.basePath}/assertion/options`);
    }
}
