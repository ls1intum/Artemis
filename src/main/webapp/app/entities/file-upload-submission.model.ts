import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';

export class FileUploadSubmission extends Submission {
    public filePath: string | null;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
    }
}
