import { BaseEntity } from 'app/shared/model/base-entity';
import * as dayjs from 'dayjs';

export enum FeedbackConflictType {
    INCONSISTENT_COMMENT = 'INCONSISTENT_COMMENT',
    INCONSISTENT_SCORE = 'INCONSISTENT_SCORE',
    INCONSISTENT_FEEDBACK = 'INCONSISTENT_FEEDBACK',
}

export class FeedbackConflict implements BaseEntity {
    public id?: number;
    public conflict?: boolean;
    public conflictingFeedbackId?: number;
    public createdAt?: dayjs.Dayjs;
    public solvedAt?: dayjs.Dayjs;
    public type?: FeedbackConflictType;
    public discard?: boolean;

    constructor() {}
}
