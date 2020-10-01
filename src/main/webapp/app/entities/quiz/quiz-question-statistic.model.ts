import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { QuizStatistic } from 'app/entities/quiz/quiz-statistic.model';

export class QuizQuestionStatistic extends QuizStatistic {
    public ratedCorrectCounter?: number;
    public unRatedCorrectCounter?: number;
    public question?: QuizQuestion;

    constructor() {
        super();
    }
}
