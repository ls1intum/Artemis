import { BaseEntity } from './../../shared';

export class ApollonDiagram implements BaseEntity {
    constructor(
        public id?: number,
        public title?: string,
        public jsonRepresentation?: string,
    ) {
    }
}
