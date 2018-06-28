import { BaseEntity } from './../../shared';

export class QuizSubmission implements BaseEntity {
    constructor(
        public id?: number,
        public submittedAnswers?: BaseEntity[],
    ) {
    }
}
