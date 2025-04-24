import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizStatistic } from 'app/quiz/shared/entities/quiz-statistic.model';

export class QuizQuestionStatistic extends QuizStatistic {
    public ratedCorrectCounter?: number;
    public unRatedCorrectCounter?: number;
    public question?: QuizQuestion;

    constructor() {
        super();
    }
}
