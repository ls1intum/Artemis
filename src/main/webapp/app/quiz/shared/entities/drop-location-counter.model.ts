import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropQuestionStatistic } from 'app/quiz/shared/entities/drag-and-drop-question-statistic.model';

export class DropLocationCounter extends QuizStatisticCounter {
    public dropLocation?: DropLocation;
    public dragAndDropQuestionStatistic?: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
