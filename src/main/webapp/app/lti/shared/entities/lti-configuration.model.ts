import { BaseEntity } from 'app/foundation/model/base-entity';

export interface LtiPlatformConfiguration extends BaseEntity {
    id?: number;
    registrationId?: string;
    originalUrl?: string;
    customName?: string;
    clientId: string;
    authorizationUri: string;
    jwkSetUri: string;
    tokenUri: string;
}
