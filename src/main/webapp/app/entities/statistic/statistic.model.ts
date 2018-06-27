import { BaseEntity } from './../../shared';

export class Statistic implements BaseEntity {
    constructor(
        public id?: number,
        public released?: boolean,
        public participantsRated?: number,
        public participantsUnrated?: number,
    ) {
        this.released = false;
    }
}
