import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';

export class DragAndDropQuestion extends QuizQuestion {
    public importedFiles?: Map<string, Blob>;
    public backgroundFilePath?: string;
    public dropLocations?: DropLocation[];
    public dragItems?: DragItem[];
    public correctMappings?: DragAndDropMapping[];

    constructor() {
        super(QuizQuestionType.DRAG_AND_DROP);
    }
}
