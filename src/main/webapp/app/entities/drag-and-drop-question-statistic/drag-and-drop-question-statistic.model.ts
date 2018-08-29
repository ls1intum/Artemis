import { DropLocationCounter } from '../drop-location-counter';
import { QuestionStatistic } from '../question-statistic';

export class DragAndDropQuestionStatistic extends QuestionStatistic {

    public dropLocationCounters: DropLocationCounter[];

    constructor() {
        super();
    }
}
