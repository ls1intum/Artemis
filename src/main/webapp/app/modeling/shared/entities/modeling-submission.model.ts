import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';

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
