import { SubmittedAnswer } from 'app/entities/submitted-answer/submitted-answer.model';
import { DragAndDropMapping } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping.model';
import { QuizQuestionType } from 'app/entities/quiz-question/quiz-question.model';

export class DragAndDropSubmittedAnswer extends SubmittedAnswer {
    public mappings: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
