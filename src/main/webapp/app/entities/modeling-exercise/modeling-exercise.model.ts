import { BaseEntity } from './../../shared';

export class ModelingExercise implements BaseEntity {
    constructor(
        public id?: number,
        public baseFilePath?: string,
    ) {
    }
}
