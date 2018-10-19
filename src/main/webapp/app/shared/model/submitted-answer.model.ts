import { IQuestion } from 'app/shared/model//question.model';
import { IQuizSubmission } from 'app/shared/model//quiz-submission.model';

export interface ISubmittedAnswer {
    id?: number;
    scoreInPoints?: number;
    question?: IQuestion;
    submission?: IQuizSubmission;
}

export class SubmittedAnswer implements ISubmittedAnswer {
    constructor(public id?: number, public scoreInPoints?: number, public question?: IQuestion, public submission?: IQuizSubmission) {}
}
