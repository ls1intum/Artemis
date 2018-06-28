import { BaseEntity } from './../../shared';

export class SubmittedAnswer implements BaseEntity {
    constructor(
        public id?: number,
        public question?: BaseEntity,
        public submission?: BaseEntity,
    ) {
    }
}
