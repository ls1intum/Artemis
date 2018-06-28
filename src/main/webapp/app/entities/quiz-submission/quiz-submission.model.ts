import { Submission } from '../submission';
import { SubmittedAnswer } from '../submitted-answer';

export class QuizSubmission extends Submission {
    constructor(
        public id?: number,
        public scoreInPoints?: number,
        public submittedAnswers?: SubmittedAnswer[],
    ) {
        super();
    }
}
