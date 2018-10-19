import { ISubmittedAnswer } from 'app/shared/model//submitted-answer.model';

export interface IQuizSubmission {
    id?: number;
    scoreInPoints?: number;
    submittedAnswers?: ISubmittedAnswer[];
}

export class QuizSubmission implements IQuizSubmission {
    constructor(public id?: number, public scoreInPoints?: number, public submittedAnswers?: ISubmittedAnswer[]) {}
}
