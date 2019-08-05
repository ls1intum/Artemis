import { Submission, SubmissionExerciseType } from '../submission';

export class FileUploadSubmission extends Submission {
    public filePath: string | null;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
    }
}
