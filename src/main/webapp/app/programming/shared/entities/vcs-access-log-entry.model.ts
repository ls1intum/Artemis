import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';

export interface VcsAccessLogDTO extends BaseEntity {
    id?: number;
    userId?: number;
    name?: string;
    email?: string;
    repositoryActionType: string;
    authenticationMechanism: string;
    commitHash?: string;
    timestamp: dayjs.Dayjs;
}
