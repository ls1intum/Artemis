import { BaseEntity } from './../../shared';

export class ModelingSubmission implements BaseEntity {
    constructor(
        public id?: number,
        public submissionPath?: string,
    ) {
    }
}
