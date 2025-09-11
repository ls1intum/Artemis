import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';

export class QuizTrainingAnswer {
    public submittedAnswer?: SubmittedAnswer;
    public isRated?: boolean;

    constructor() {}
}
