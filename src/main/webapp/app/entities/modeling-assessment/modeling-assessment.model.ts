import { BaseEntity } from './../../shared';

export class ModelingAssessment implements BaseEntity {
    constructor(
        public id?: string,
        public type?: string,
        public credits?: number,
        public comment?: string,
    ) {
    }
}
