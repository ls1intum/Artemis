import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';

export class DragAndDropSubmittedAnswer extends SubmittedAnswer {
    public mappings?: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
