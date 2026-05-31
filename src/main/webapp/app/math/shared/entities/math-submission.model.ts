import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { DerivationStep } from './derivation-step.model';
import { MathExercise } from './math-exercise.model';

export interface MathParticipation {
    id?: number;
    studentLogin?: string;
    studentName?: string;
    exercise?: MathExercise;
}

export class MathSubmission extends Submission {
    public steps?: DerivationStep[];
    declare participation?: MathParticipation;

    constructor() {
        super(SubmissionExerciseType.MATH);
    }
}
