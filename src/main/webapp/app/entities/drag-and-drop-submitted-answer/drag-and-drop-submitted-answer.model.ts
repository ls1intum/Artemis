import { SubmittedAnswer } from '../submitted-answer';
import { DragAndDropMapping } from '../drag-and-drop-mapping';
import { QuestionType } from '../question';

export class DragAndDropSubmittedAnswer extends SubmittedAnswer {

    public mappings: DragAndDropMapping[];

    constructor() {
        super(QuestionType.DRAG_AND_DROP);
    }
}
