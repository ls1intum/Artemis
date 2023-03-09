import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';

export class DragAndDropQuestion extends QuizQuestion {
    public backgroundFilePath?: string;
    public dropLocations?: DropLocation[];
    public dragItems?: DragItem[];
    public correctMappings?: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
