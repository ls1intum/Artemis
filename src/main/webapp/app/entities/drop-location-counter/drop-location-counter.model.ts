import { QuizStatisticCounter } from 'app/entities/quiz-statistic-counter/quiz-statistic-counter.model';
import { DropLocation } from 'app/entities/drop-location/drop-location.model';
import { DragAndDropQuestionStatistic } from 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.model';

export class DropLocationCounter extends QuizStatisticCounter {
    public dropLocation: DropLocation;
    public dragAndDropQuestionStatistic: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
