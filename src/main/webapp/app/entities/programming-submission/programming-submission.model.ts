import { Submission, SubmissionExerciseType } from '../submission';

export class ProgrammingSubmission extends Submission {

    public commitHash: string;

    constructor() {
        super(SubmissionExerciseType.PROGRAMMING);
    }
}
