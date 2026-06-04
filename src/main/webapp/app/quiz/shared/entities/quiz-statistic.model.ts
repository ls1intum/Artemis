import { BaseEntity } from 'app/foundation/model/base-entity';

export abstract class QuizStatistic implements BaseEntity {
    public id?: number;
    public participantsRated?: number;
    public participantsUnrated?: number;

    protected constructor() {}
}
