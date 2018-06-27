import { SubmittedAnswer } from '../submitted-answer';
import { DragAndDropMapping } from '../drag-and-drop-mapping';

export class DragAndDropSubmittedAnswer extends SubmittedAnswer {
    constructor(
        public id?: number,
        public mappings?: DragAndDropMapping[],
    ) {
        super();
    }
}
