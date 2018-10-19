import { IQuestion } from 'app/shared/model//question.model';

export interface IQuestionStatistic {
    id?: number;
    ratedCorrectCounter?: number;
    unRatedCorrectCounter?: number;
    question?: IQuestion;
}

export class QuestionStatistic implements IQuestionStatistic {
    constructor(
        public id?: number,
        public ratedCorrectCounter?: number,
        public unRatedCorrectCounter?: number,
        public question?: IQuestion
    ) {}
}
