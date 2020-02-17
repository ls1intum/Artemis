import { QuizQuestionStatistic } from 'app/entities/quiz-question-statistic/quiz-question-statistic.model';
import { DropLocationCounter } from 'app/entities/drop-location-counter/drop-location-counter.model';

export class DragAndDropQuestionStatistic extends QuizQuestionStatistic {
    public dropLocationCounters: DropLocationCounter[];

    constructor() {
        super();
    }
}
