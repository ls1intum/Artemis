import { BaseEntity } from 'app/shared/model/base-entity';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { generate } from 'app/exercises/quiz/manage/temp-id';

export interface CanBecomeInvalid {
    invalid?: boolean;
}

export class DropLocation implements BaseEntity, CanBecomeInvalid {
    public id?: number;
    public tempID?: number;
    public posX?: number;
    public posY?: number;
    public width?: number;
    public height?: number;
    public invalid?: boolean;
    public question?: DragAndDropQuestion;

    constructor() {
        this.tempID = generate();
        this.invalid = false; // default value
    }
}
