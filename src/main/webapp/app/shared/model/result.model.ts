import { Moment } from 'moment';
import { ISubmission } from 'app/shared/model/submission.model';
import { IFeedback } from 'app/shared/model/feedback.model';
import { IParticipation } from 'app/shared/model/participation.model';

export interface IResult {
    id?: number;
    resultString?: string;
    completionDate?: Moment;
    successful?: boolean;
    buildArtifact?: boolean;
    score?: number;
    submission?: ISubmission;
    feedbacks?: IFeedback[];
    participation?: IParticipation;
}

export class Result implements IResult {
    constructor(
        public id?: number,
        public resultString?: string,
        public completionDate?: Moment,
        public successful?: boolean,
        public buildArtifact?: boolean,
        public score?: number,
        public submission?: ISubmission,
        public feedbacks?: IFeedback[],
        public participation?: IParticipation
    ) {
        this.successful = this.successful || false;
        this.buildArtifact = this.buildArtifact || false;
    }
}
