import { BaseEntity } from './../../shared';

export class Feedback implements BaseEntity {
    constructor(
        public id?: number,
        public text?: string,
        public detailText?: string,
        public result?: BaseEntity,
    ) {
    }
}
