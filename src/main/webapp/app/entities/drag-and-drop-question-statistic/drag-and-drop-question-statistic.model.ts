import { DropLocationCounter } from '../drop-location-counter';
import { QuizQuestionStatistic } from '../quiz-question-statistic';

export class DragAndDropQuestionStatistic extends QuizQuestionStatistic {

    public dropLocationCounters: DropLocationCounter[];

    constructor() {
        super();
    }
}
