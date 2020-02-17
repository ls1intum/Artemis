import { BaseEntity } from 'app/shared/model/base-entity';
import { generate } from 'app/quiz/edit/temp-id';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question/drag-and-drop-question.model';

export class DropLocation implements BaseEntity {
    public id: number;
    public tempID: number;
    public posX: number;
    public posY: number;
    public width: number;
    public height: number;
    public invalid = false; // default value
    public question: DragAndDropQuestion;

    constructor() {
        this.tempID = generate();
    }
}
