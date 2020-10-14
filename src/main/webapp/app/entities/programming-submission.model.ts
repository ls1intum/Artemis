import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class ProgrammingSubmission extends Submission {
    public commitHash?: string;
    public buildFailed?: boolean;
    public buildArtifact?: boolean; // default value (whether the result includes a build artifact or not)

    constructor() {
        super(SubmissionExerciseType.PROGRAMMING);
    }

    /**
     * Returns an empty programming submission with the {@link Submission#isSynced} flag set to true.
     * This is required to update the navigation bar GUI in the exam conduction.
     */
    // TODO: this could be removed after the latest submission for programming exercises if fetched through websockets and passed to the exam participation
    static createInitialCleanSubmissionForExam(): ProgrammingSubmission {
        const submission = new ProgrammingSubmission();
        submission.isSynced = true;
        submission.submitted = false;
        return submission;
    }
}
