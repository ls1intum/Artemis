import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { DerivationStep } from './derivation-step.model';
import { ProofExercise } from './proof-exercise.model';

export interface ProofParticipation {
    id?: number;
    studentLogin?: string;
    studentName?: string;
    exercise?: ProofExercise;
}

export class ProofSubmission extends Submission {
    public steps?: DerivationStep[];
    declare participation?: ProofParticipation;

    constructor() {
        super(SubmissionExerciseType.PROOF);
    }
}
