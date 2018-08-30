import { Question, QuestionType } from '../question';
import { DropLocation } from '../drop-location';
import { DragItem } from '../drag-item';
import { DragAndDropMapping } from '../drag-and-drop-mapping';

export class DragAndDropQuestion extends Question {

    public backgroundFilePath: string;
    public dropLocations: DropLocation[];
    public dragItems: DragItem[];
    public correctMappings: DragAndDropMapping[];

    constructor() {
        super(QuestionType.DRAG_AND_DROP);
    }
}
