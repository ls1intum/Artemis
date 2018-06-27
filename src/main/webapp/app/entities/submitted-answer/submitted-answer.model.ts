import { BaseEntity } from './../../shared';
import { Question } from '../question';
import { QuizSubmission } from '../quiz-submission';

export class SubmittedAnswer implements BaseEntity {
    constructor(
        public id?: number,
        public scoreInPoints?: number,
        public question?: Question,
        public submission?: QuizSubmission,
    ) {
    }
}
