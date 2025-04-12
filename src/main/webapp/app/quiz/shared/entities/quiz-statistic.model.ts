import { BaseEntity } from 'app/shared/model/base-entity';

export abstract class QuizStatistic implements BaseEntity {
    public id?: number;
    public participantsRated?: number;
    public participantsUnrated?: number;

    protected constructor() {}
}
