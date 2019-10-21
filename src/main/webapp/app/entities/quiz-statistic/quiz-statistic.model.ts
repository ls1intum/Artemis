import { BaseEntity } from 'app/shared';

export abstract class QuizStatistic implements BaseEntity {
    public id: number;
    public participantsRated: number;
    public participantsUnrated: number;

    protected constructor() {}
}
