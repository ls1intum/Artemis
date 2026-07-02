import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { MathExercise } from './math-exercise.model';

export interface MathParticipation {
    id?: number;
    studentLogin?: string;
    studentName?: string;
    exercise?: MathExercise;
}

export class MathSubmission extends Submission {
    public content?: string;
    declare participation?: MathParticipation;

    constructor() {
        super(SubmissionExerciseType.MATH);
    }
}
