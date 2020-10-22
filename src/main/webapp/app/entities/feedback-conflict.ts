import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';

export enum FeedbackConflictType {
    INCONSISTENT_COMMENT = 'INCONSISTENT_COMMENT',
    INCONSISTENT_SCORE = 'INCONSISTENT_SCORE',
    INCONSISTENT_FEEDBACK = 'INCONSISTENT_FEEDBACK',
}

export class FeedbackConflict implements BaseEntity {
    public id?: number;
    public conflict?: boolean;
    public conflictingFeedbackId?: number;
    public createdAt?: Moment;
    public solvedAt?: Moment;
    public type?: FeedbackConflictType;
    public discard?: boolean;

    constructor() {}
}
