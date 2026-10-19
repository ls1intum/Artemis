import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';

export interface ModelElementCount {
    elementId: string;
    numberOfOtherElements: number;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ModelingSubmission extends Submission {
    public model?: string;
    public explanationText?: string;
    public similarElements?: ModelElementCount[];
    constructor() {
        super(SubmissionExerciseType.MODELING);
    }
}
