import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { DerivationStep } from './derivation-step.model';

export class ProofSubmission extends Submission {
    public steps?: DerivationStep[];

    constructor() {
        super(SubmissionExerciseType.PROOF);
    }
}
