import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class OtherModelElementCount {
    elementId: string;
    numberOfOtherElements: number;
}

export class ModelingSubmission extends Submission {
    public model?: string;
    public explanationText?: string;
    public similarElements?: OtherModelElementCount[];
    constructor() {
        super(SubmissionExerciseType.MODELING);
    }
}
