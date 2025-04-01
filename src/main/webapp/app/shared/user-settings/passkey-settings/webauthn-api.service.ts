import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { PasskeyOptions } from 'app/shared/user-settings/passkey-settings/entities/passkey-options.model';
import { AssertionServerOptions } from 'app/shared/user-settings/passkey-settings/passkey-settings.component';
import { Injectable } from '@angular/core';
import { CreatePasskeyDTO } from 'app/shared/user-settings/passkey-settings/dto/create-passkey.dto';

/**
 * Note: [WebAuthn4j](https://github.com/webauthn4j/webauthn4j) exposes the endpoints, the endpoints are not explicitly defined in a resource
 */
@Injectable({ providedIn: 'root' })
export class WebauthnApiService extends BaseApiHttpService {
    /**
     * The endpoints are provided by spring and not explicitly defined in a resource,
     * therefore there is no "/api" prefix but instead a "webauthn" prefix.
     */
    protected baseUrl = 'webauthn';

    async getWebauthnOptions(): Promise<PasskeyOptions> {
        return await this.post<PasskeyOptions>(`register/options`);
    }

    async registerPasskey(createPasskeyDto: CreatePasskeyDTO): Promise<any> {
        return await this.post<CreatePasskeyDTO>(`register`, createPasskeyDto);
    }

    async getAssertionOptions(): Promise<AssertionServerOptions> {
        return await this.get<AssertionServerOptions>(`authenticate/options`);
    }
}
