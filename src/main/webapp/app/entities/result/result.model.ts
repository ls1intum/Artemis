import { BaseEntity, User } from './../../shared';
import { Feedback } from '../feedback';
import { Submission } from '../submission';
import { Participation } from '../participation';

export const enum AssessmentType {
    'AUTOMATIC',
    'MANUAL',
    'SEMIAUTOMATIC'
}

export class Result implements BaseEntity {
    constructor(
        public id?: number,
        public resultString?: string,
        public completionDate?: any,
        public successful?: boolean,
        public buildArtifact?: boolean,
        public hasFeedback?: boolean,
        public score?: number,
        public assessmentType?: AssessmentType,
        public submission?: Submission,
        public assessor?: User,
        public feedbacks?: Feedback[],
        public participation?: Participation,
        public rated?: boolean,
        public optimal?: boolean,
    ) {
        this.successful = false;
        this.buildArtifact = false;
    }
}
