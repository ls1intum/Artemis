import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';
import { DropLocationCounter } from 'app/quiz/shared/entities/drop-location-counter.model';

export class DragAndDropQuestionStatistic extends QuizQuestionStatistic {
    public dropLocationCounters?: DropLocationCounter[];

    constructor() {
        super();
    }
}
