import { QuizQuestion, QuizQuestionType } from '../quiz-question';
import { DropLocation } from '../drop-location';
import { DragItem } from '../drag-item';
import { DragAndDropMapping } from '../drag-and-drop-mapping';

export class DragAndDropQuestion extends QuizQuestion {

    public backgroundFilePath: string;
    public dropLocations: DropLocation[];
    public dragItems: DragItem[];
    public correctMappings: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
