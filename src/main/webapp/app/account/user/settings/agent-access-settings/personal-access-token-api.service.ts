import { Service } from '@angular/core';
import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';
import { CreatePersonalAccessTokenDTO, CreatedPersonalAccessTokenDTO, PersonalAccessTokenDTO } from 'app/account/user/settings/agent-access-settings/dto/personal-access-token.dto';

@Service()
export class PersonalAccessTokenApiService extends BaseApiHttpService {
    private readonly basePath = 'account';

    async getTokens(): Promise<PersonalAccessTokenDTO[]> {
        return await this.get<PersonalAccessTokenDTO[]>(`${this.basePath}/personal-access-tokens`);
    }

    async getScopes(): Promise<string[]> {
        return await this.get<string[]>(`${this.basePath}/personal-access-token-scopes`);
    }

    async createToken(request: CreatePersonalAccessTokenDTO): Promise<CreatedPersonalAccessTokenDTO> {
        return await this.post<CreatedPersonalAccessTokenDTO>(`${this.basePath}/personal-access-tokens`, request);
    }

    async revokeToken(tokenId: number): Promise<void> {
        return await this.delete(`${this.basePath}/personal-access-tokens/${tokenId}`);
    }
}
