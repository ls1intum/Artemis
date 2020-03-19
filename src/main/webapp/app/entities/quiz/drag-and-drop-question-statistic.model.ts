import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { DropLocationCounter } from 'app/entities/quiz/drop-location-counter.model';

export class DragAndDropQuestionStatistic extends QuizQuestionStatistic {
    public dropLocationCounters: DropLocationCounter[];

    constructor() {
        super();
    }
}
