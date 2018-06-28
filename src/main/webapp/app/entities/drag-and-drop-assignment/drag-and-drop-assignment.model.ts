import { BaseEntity } from './../../shared';

export class DragAndDropAssignment implements BaseEntity {
    constructor(
        public id?: number,
        public item?: BaseEntity,
        public location?: BaseEntity,
        public submittedAnswer?: BaseEntity,
    ) {
    }
}
