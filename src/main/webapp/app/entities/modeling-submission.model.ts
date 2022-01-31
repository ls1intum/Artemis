import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class ModelElementCount {
    elementId: string;
    numberOfOtherElements: number;
}

export class ModelingSubmission extends Submission {
    public model?: string;
    public explanationText?: string;
    public similarElements?: ModelElementCount[];
    constructor() {
        super(SubmissionExerciseType.MODELING);
    }
}
