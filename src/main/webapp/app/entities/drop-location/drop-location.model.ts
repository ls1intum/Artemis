import { BaseEntity } from './../../shared';
import { DragAndDropQuestion } from '../drag-and-drop-question';

export class DropLocation implements BaseEntity {
    constructor(
        public id?: number,
        public tempID?: number,
        public posX?: number,
        public posY?: number,
        public width?: number,
        public height?: number,
        public invalid?: boolean,
        public question?: DragAndDropQuestion,
    ) {
    }
}
