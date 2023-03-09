import { DropLocationCounter } from 'app/entities/quiz/drop-location-counter.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';

export class DragAndDropQuestionStatistic extends QuizQuestionStatistic {
    public dropLocationCounters?: DropLocationCounter[];

    constructor() {
        super();
    }
}
