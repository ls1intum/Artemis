import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
// import { PasskeyOptions } from 'app/core/user/settings/passkey-settings/entities/passkey-options.model';
import { Injectable } from '@angular/core';
import { RegisterPasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/register-passkey.dto';
import { PasskeyLoginResponseDTO } from 'app/core/user/settings/passkey-settings/dto/passkey-login-response.dto';
import { RegisterPasskeyResponseDTO } from 'app/core/user/settings/passkey-settings/dto/register-passkey-response.dto';

/**
 * Note: [WebAuthn4j](https://github.com/webauthn4j/webauthn4j) exposes the endpoints, the endpoints are not explicitly defined in a resource
 */
@Injectable({ providedIn: 'root' })
export class WebauthnApiService extends BaseApiHttpService {
    /**
     * The endpoints are provided by spring and not explicitly defined in a resource, therefore there is no "/api" prefix
     */
    protected baseUrl = '';

    /**
     * @see {@link https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html#passkeys-register-options}
     */
    async getRegistrationOptions(): Promise<PublicKeyCredentialCreationOptions> {
        return await this.post<PublicKeyCredentialCreationOptions>(`webauthn/register/options`);
    }

    /**
     * @see {@link https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html#passkeys-register-create}
     */
    async registerPasskey(registerPasskeyDto: RegisterPasskeyDTO): Promise<RegisterPasskeyResponseDTO> {
        return await this.post<RegisterPasskeyResponseDTO>(`webauthn/register`, registerPasskeyDto);
    }

    /**
     * @see {@link https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html#passkeys-verify-options}
     */
    async getAuthenticationOptions(): Promise<PublicKeyCredentialRequestOptions> {
        return await this.post<PublicKeyCredentialRequestOptions>(`webauthn/authenticate/options`);
    }

    /**
     * @see {@link https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html#passkeys-verify-get}
     */
    async loginWithPasskey(publicKeyCredential: PublicKeyCredential): Promise<PasskeyLoginResponseDTO> {
        return await this.post<PasskeyLoginResponseDTO>(`login/webauthn`, publicKeyCredential);
    }
}
