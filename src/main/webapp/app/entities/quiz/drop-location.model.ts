import { BaseEntity } from 'app/shared/model/base-entity';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { generate } from 'app/exercises/quiz/manage/temp-id';

export interface CanBecomeInvalid {
    invalid: boolean;
}

export class BaseEntityWithTempId implements BaseEntity {
    public id?: number;
    public tempID?: number;
    constructor() {
        this.tempID = generate();
    }
}

export class DropLocation extends BaseEntityWithTempId implements CanBecomeInvalid {
    public posX?: number;
    public posY?: number;
    public width?: number;
    public height?: number;
    public invalid = false; // default value
    public question?: DragAndDropQuestion;

    constructor() {
        super();
    }
}
