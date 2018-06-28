import { BaseEntity } from './../../shared';

export class Result implements BaseEntity {
    constructor(
        public id?: number,
        public resultString?: string,
        public completionDate?: any,
        public successful?: boolean,
        public buildArtifact?: boolean,
        public score?: number,
        public submission?: BaseEntity,
        public feedbacks?: BaseEntity[],
        public participation?: BaseEntity,
    ) {
        this.successful = false;
        this.buildArtifact = false;
    }
}
