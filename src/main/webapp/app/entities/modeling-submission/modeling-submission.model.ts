import { Submission, SubmissionExerciseType } from '../submission';

export class ModelingSubmission extends Submission {

    public model: string;
    public explanationText: string;

    constructor() {
        super(SubmissionExerciseType.MODELING);
    }
}
