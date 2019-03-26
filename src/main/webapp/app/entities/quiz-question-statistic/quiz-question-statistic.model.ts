import { QuizStatistic } from '../quiz-statistic';
import { QuizQuestion } from '../quiz-question';

export class QuizQuestionStatistic extends QuizStatistic {

    public ratedCorrectCounter: number;
    public unRatedCorrectCounter: number;
    public question: QuizQuestion;

    constructor() {
        super();
    }
}
