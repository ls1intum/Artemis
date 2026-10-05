import { BaseEntity } from 'app/foundation/model/base-entity';

export abstract class QuizStatistic implements BaseEntity {
    public id?: number;
    /** Latest-result counts per rating bucket; the same participation can occur once in each bucket. */
    public participantsRated?: number;
    public participantsUnrated?: number;

    protected constructor() {}
}
