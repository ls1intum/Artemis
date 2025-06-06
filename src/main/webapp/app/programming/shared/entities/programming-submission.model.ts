import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import dayjs from 'dayjs/esm';
import { Result } from 'app/exercise/shared/entities/result/result.model';

export class ProgrammingSubmission extends Submission {
    public commitHash?: string;
    public buildFailed?: boolean;
    public isProcessing?: boolean;
    public buildStartDate?: dayjs.Dayjs;
    public estimatedCompletionDate?: dayjs.Dayjs;

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
export class CommitInfo {
    hash?: string;
    message?: string;
    timestamp?: dayjs.Dayjs;
    author?: string;
    authorEmail?: string;
    result?: Result;
}
