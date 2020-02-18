import { QuizQuestion } from 'app/entities/quiz-question/quiz-question.model';
import { QuizStatistic } from 'app/entities/quiz-statistic/quiz-statistic.model';

export class QuizQuestionStatistic extends QuizStatistic {
    public ratedCorrectCounter: number;
    public unRatedCorrectCounter: number;
    public question: QuizQuestion;

    constructor() {
        super();
    }
}
