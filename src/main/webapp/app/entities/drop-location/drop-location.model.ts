import { BaseEntity } from 'app/shared';
import { DragAndDropQuestion } from '../drag-and-drop-question';
import { generate } from 'app/quiz/edit/temp-id';

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
