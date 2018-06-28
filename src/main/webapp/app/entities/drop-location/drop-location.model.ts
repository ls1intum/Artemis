import { BaseEntity } from './../../shared';

export class DropLocation implements BaseEntity {
    constructor(
        public id?: number,
        public posX?: number,
        public posY?: number,
        public width?: number,
        public height?: number,
        public question?: BaseEntity,
    ) {
    }
}
