import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

export class VcsAccessLogDTO implements BaseEntity {
    public id?: number;
    public userId?: number;
    public name?: string;
    public email?: string;
    public repositoryActionType: string;
    public authenticationMechanism: string;
    public commitHash?: string;
    public timestamp: dayjs.Dayjs;
}
