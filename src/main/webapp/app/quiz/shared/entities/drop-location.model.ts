import { BaseEntity } from 'app/foundation/model/base-entity';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { generate } from 'app/quiz/manage/util/temp-id';

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

// Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200].
// Kept here and not next to the diagram generator, so that the question editor does not import the Apollon editor to read it.
export const MAX_SIZE_UNIT = 200;

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
