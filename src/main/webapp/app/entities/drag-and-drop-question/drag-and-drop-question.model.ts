import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz-question/quiz-question.model';
import { DropLocation } from 'app/entities/drop-location/drop-location.model';
import { DragItem } from 'app/entities/drag-item/drag-item.model';
import { DragAndDropMapping } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping.model';

export class DragAndDropQuestion extends QuizQuestion {
    public backgroundFilePath: string;
    public dropLocations: DropLocation[];
    public dragItems: DragItem[];
    public correctMappings: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
