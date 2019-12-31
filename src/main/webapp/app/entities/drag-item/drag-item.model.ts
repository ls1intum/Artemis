import { BaseEntity } from 'app/shared';
import { DragAndDropQuestion } from '../drag-and-drop-question';
import { generate } from 'app/quiz/edit/temp-id';

export class DragItem implements BaseEntity {
    public id: number;
    public tempID: number;
    public pictureFilePath: string | null;
    public text: string | null;
    public question: DragAndDropQuestion;
    public invalid = false; // default value

    constructor() {
        this.tempID = generate();
    }
}
