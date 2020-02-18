import { BaseEntity } from 'app/shared/model/base-entity';

export class QuizStatisticCounter implements BaseEntity {
    public id: number;
    public ratedCounter: number;
    public unRatedCounter: number;

    constructor() {}
}
