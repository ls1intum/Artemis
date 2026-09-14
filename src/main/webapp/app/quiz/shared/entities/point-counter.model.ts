import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';

export class PointCounter extends QuizStatisticCounter {
    public points?: number;

    constructor() {
        super();
    }
}
