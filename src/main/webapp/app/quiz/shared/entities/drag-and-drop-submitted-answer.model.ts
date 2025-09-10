import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';

export class DragAndDropSubmittedAnswer extends SubmittedAnswer {
    public mappings?: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
