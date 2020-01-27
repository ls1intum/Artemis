import { Submission, SubmissionExerciseType } from '../submission';

export class ProgrammingSubmission extends Submission {
    public commitHash: string;
    public buildFailed: boolean;
    public buildArtifact: boolean; // default value (whether the result includes a build artifact or not)

    constructor() {
        super(SubmissionExerciseType.PROGRAMMING);
    }
}
