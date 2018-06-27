import { BaseEntity } from './../../shared';

export class StatisticCounter implements BaseEntity {
    constructor(
        public id?: number,
        public ratedCounter?: number,
        public unRatedCounter?: number,
    ) {
    }
}
