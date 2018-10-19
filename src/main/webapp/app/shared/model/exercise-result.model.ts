import { Moment } from 'moment';
import { IUser } from 'app/core/user/user.model';
import { IFeedback } from 'app/shared/model//feedback.model';
import { ISubmission } from 'app/shared/model//submission.model';
import { IParticipation } from 'app/shared/model//participation.model';

export const enum AssessmentType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
    SEMIAUTOMATIC = 'SEMIAUTOMATIC'
}

export interface IExerciseResult {
    id?: number;
    resultString?: string;
    completionDate?: Moment;
    successful?: boolean;
    buildArtifact?: boolean;
    score?: number;
    rated?: boolean;
    hasFeedback?: boolean;
    assessmentType?: AssessmentType;
    assessor?: IUser;
    feedbacks?: IFeedback[];
    submission?: ISubmission;
    participation?: IParticipation;
}

export class ExerciseResult implements IExerciseResult {
    constructor(
        public id?: number,
        public resultString?: string,
        public completionDate?: Moment,
        public successful?: boolean,
        public buildArtifact?: boolean,
        public score?: number,
        public rated?: boolean,
        public hasFeedback?: boolean,
        public assessmentType?: AssessmentType,
        public assessor?: IUser,
        public feedbacks?: IFeedback[],
        public submission?: ISubmission,
        public participation?: IParticipation
    ) {
        this.successful = this.successful || false;
        this.buildArtifact = this.buildArtifact || false;
        this.rated = this.rated || false;
        this.hasFeedback = this.hasFeedback || false;
    }
}
