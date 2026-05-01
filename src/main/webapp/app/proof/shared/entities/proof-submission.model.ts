import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';

export class ProofSubmission extends Submission {
    public text?: string;
    public studentCheckboxState?: boolean;

    constructor() {
        super(SubmissionExerciseType.PROOF);
    }
}
