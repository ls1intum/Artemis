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
    /** Whether a build agent made the access, for example to clone the repository for a build, rather than a person. */
    buildAgent?: boolean;
    timestamp: dayjs.Dayjs;
}
