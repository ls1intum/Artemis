import { BaseEntity } from './../../shared';

export class MultipleChoiceQuestion implements BaseEntity {
    constructor(
        public id?: number,
        public answerOptions?: BaseEntity[],
    ) {
    }
}
