import { BaseEntity } from 'app/shared/model/base-entity';

export class LtiPlatformConfiguration implements BaseEntity {
    public id?: number;
    public registrationId?: string;
    public originalUrl?: string;
    public customName?: string;
    public clientId: string;
    public authorizationUri: string;
    public jwkSetUri: string;
    public tokenUri: string;
}
