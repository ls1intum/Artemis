import { BaseEntity } from './../../shared';

export class AnswerOption implements BaseEntity {
    constructor(
        public id?: number,
        public text?: string,
        public hint?: string,
        public explanation?: string,
        public isCorrect?: boolean,
        public question?: BaseEntity,
    ) {
        this.isCorrect = false;
    }
}
