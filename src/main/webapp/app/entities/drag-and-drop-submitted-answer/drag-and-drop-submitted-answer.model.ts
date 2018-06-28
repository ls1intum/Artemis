import { BaseEntity } from './../../shared';

export class DragAndDropSubmittedAnswer implements BaseEntity {
    constructor(
        public id?: number,
        public assignments?: BaseEntity[],
    ) {
    }
}
