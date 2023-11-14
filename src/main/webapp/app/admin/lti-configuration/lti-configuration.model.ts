import { BaseEntity } from 'app/shared/model/base-entity';

export class LtiPlatformConfiguration implements BaseEntity {
    public id?: number;
    public ltiKey?: string;
    public ltiSecret?: string;
    public registrationId?: string;
    public issuer?: string;
    public clientId?: string;
    public authorizationUri?: string;
    public jwkSetUri?: string;
    public tokenUri?: string;
}
