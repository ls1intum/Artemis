import { QuizStatisticCounter } from 'app/entities/quiz/quiz-statistic-counter.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropQuestionStatistic } from 'app/entities/quiz/drag-and-drop-question-statistic.model';

export class DropLocationCounter extends QuizStatisticCounter {
    public dropLocation?: DropLocation;
    public dragAndDropQuestionStatistic?: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
