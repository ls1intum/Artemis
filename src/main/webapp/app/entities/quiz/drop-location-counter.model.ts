import { DragAndDropQuestionStatistic } from 'app/entities/quiz/drag-and-drop-question-statistic.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';

export class DropLocationCounter extends QuizStatisticCounter {
    public dropLocation?: DropLocation;
    public dragAndDropQuestionStatistic?: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
