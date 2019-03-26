import { SubmittedAnswer } from '../submitted-answer';
import { DragAndDropMapping } from '../drag-and-drop-mapping';
import { QuizQuestionType } from '../quiz-question';

export class DragAndDropSubmittedAnswer extends SubmittedAnswer {

    public mappings: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
