import { BaseEntity } from './../../shared';

export class MultipleChoiceSubmittedAnswer implements BaseEntity {
    constructor(
        public id?: number,
        public selectedOptions?: BaseEntity[],
    ) {
    }
}
