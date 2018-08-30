import { BaseEntity, User } from './../../shared';
import { Feedback } from '../feedback';
import { Submission } from '../submission';
import { Participation } from '../participation';

export const enum AssessmentType {
    AUTOMATIC = 'AUTOMATIC',
    MANUAL = 'MANUAL',
    SEMIAUTOMATIC = 'SEMIAUTOMATIC'
}

export class Result implements BaseEntity {

    public id: number;
    public resultString: string;
    public completionDate: any;
    public successful = false;      // default value
    public buildArtifact = false;   // default value (whether the result includes a build artifact or not, only used in programming exercises)
    public hasFeedback: boolean;
    public score: number;
    public assessmentType: AssessmentType;
    public submission: Submission;
    public assessor: User;
    public feedbacks: Feedback[];
    public participation: Participation;
    public rated: boolean;
    public optimal: boolean;       // only used for modeling exercises so far

    constructor() {
    }
}
