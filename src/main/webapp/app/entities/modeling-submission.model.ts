import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class OtherModelElementCount {
    elementId: string;
    numberOfOtherElements: number;
}

export class ModelingSubmission extends Submission {
    public model?: string;
    public explanationText?: string;
    public optimal?: boolean; // used by compass to determine whether a submission leads to the most learning possible
    public similarElements?: OtherModelElementCount[];
    constructor() {
        super(SubmissionExerciseType.MODELING);
    }
}
