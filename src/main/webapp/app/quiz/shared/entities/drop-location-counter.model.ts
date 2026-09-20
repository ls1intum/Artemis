import { QuizStatisticCounter } from 'app/quiz/shared/entities/quiz-statistic-counter.model';
import { DragAndDropQuestionStatistic } from 'app/quiz/shared/entities/drag-and-drop-question-statistic.model';

export class DropLocationCounter extends QuizStatisticCounter {
    // The counter's drop location is referenced by its question-scoped id (matches the server's scalar `dropLocationId` on both the REST DTO and the statistics websocket).
    public dropLocationId?: number;
    public dragAndDropQuestionStatistic?: DragAndDropQuestionStatistic;

    constructor() {
        super();
    }
}
