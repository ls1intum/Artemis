import { DropLocation } from '../drop-location';
import { DragAndDropQuestionStatistic } from '../drag-and-drop-question-statistic';
import { QuizStatisticCounter } from '../quiz-statistic-counter';

export class DropLocationCounter extends QuizStatisticCounter {

    public dropLocation: DropLocation;
    public dragAndDropQuestionStatistic: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
