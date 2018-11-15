import { BaseEntity } from 'app/shared';

export class StatisticCounter implements BaseEntity {
    public id: number;
    public ratedCounter: number;
    public unRatedCounter: number;

    constructor() {}
}
